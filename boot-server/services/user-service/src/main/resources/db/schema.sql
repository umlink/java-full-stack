-- ============================================================
-- 初始化脚本：随仓库提交 —— clone 后应用首次启动即自动建表 + 灌初始数据
-- 幂等设计：
--   1. CREATE TABLE IF NOT EXISTS —— 重复执行不报错
--   2. 初始数据仅在「表为空」时插入（NOT EXISTS 守卫）——
--      之后你的增删改全部保留，重启不会把实验数据重置回初始状态
-- ============================================================

CREATE TABLE IF NOT EXISTS t_user (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    -- 注册登录名：老 M0 创建的表格没有该列，因此允许为 NULL，不破坏已有创建行为
    username      VARCHAR(64)  NULL,
    name          VARCHAR(64)  NOT NULL,
    email         VARCHAR(128) NOT NULL,
    -- 只保存 BCrypt 单向哈希（约 60 字符），绝不存明文；M0 旧数据无密码为 NULL
    password_hash VARCHAR(128) NULL COMMENT 'BCrypt 哈希，严禁明文',
    -- 账号状态：1 正常 / 0 禁用；注册默认 1，字段冗余于 deleted 是保留状态机的扩展位
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '账号状态：1 正常 / 0 禁用',
    age           INT          NULL,
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 用户名/邮箱唯一：业务层预检查拦常规重复，这里是并发或绕过业务检查时的最后防线
    CONSTRAINT uk_user_username UNIQUE (username),
    CONSTRAINT uk_user_email UNIQUE (email)
);

-- 兼容已有库：老库 t_user 没有注册字段与唯一约束，用 IF NOT EXISTS 增量补齐且可重复执行
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS username VARCHAR(64) NULL;
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS password_hash VARCHAR(128) NULL;
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS status TINYINT NOT NULL DEFAULT 1;
ALTER TABLE t_user ADD CONSTRAINT IF NOT EXISTS uk_user_username UNIQUE (username);
ALTER TABLE t_user ADD CONSTRAINT IF NOT EXISTS uk_user_email UNIQUE (email);

-- 初始数据：仅当 t_user 为空时插入（H2 支持 INSERT ... SELECT ... WHERE NOT EXISTS）
-- 注意：首支 SELECT 必须给列起别名，否则 H2 对多支 UNION 的同名表达式列（CURRENT_TIMESTAMP）报「重名列」
INSERT INTO t_user (id, name, email, age, deleted, create_time, update_time)
SELECT id, name, email, age, deleted, create_time, update_time FROM (
    SELECT 1 AS id, 'Alice' AS name, 'alice@example.com' AS email, 25 AS age, 0 AS deleted, CURRENT_TIMESTAMP AS create_time, CURRENT_TIMESTAMP AS update_time
    UNION ALL
    SELECT 2 AS id, 'Bob' AS name, 'bob@example.com' AS email, 30 AS age, 0 AS deleted, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    UNION ALL
    SELECT 3 AS id, 'Charlie' AS name, 'charlie@example.com' AS email, 28 AS age, 0 AS deleted, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) tmp
WHERE NOT EXISTS (SELECT 1 FROM t_user);

-- RBAC 最小模型：角色与权限分开维护，两张关联表表达多对多关系；当前只为后续认证授权准备数据。
CREATE TABLE IF NOT EXISTS t_role (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(64) NOT NULL,
    name        VARCHAR(64) NOT NULL,
    create_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_role_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS t_permission (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(128) NOT NULL,
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_permission_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS t_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES t_user (id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES t_role (id)
);

CREATE TABLE IF NOT EXISTS t_role_permission (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES t_role (id),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES t_permission (id)
);

-- 每条初始记录以业务唯一键为守卫，脚本可在非空数据库上重复执行且不会覆盖学习数据。
INSERT INTO t_role (code, name)
SELECT 'ADMIN', '管理员'
WHERE NOT EXISTS (SELECT 1 FROM t_role WHERE code = 'ADMIN');

INSERT INTO t_role (code, name)
SELECT 'USER', '普通用户'
WHERE NOT EXISTS (SELECT 1 FROM t_role WHERE code = 'USER');

INSERT INTO t_permission (code, name)
SELECT 'user:read', '查看用户'
WHERE NOT EXISTS (SELECT 1 FROM t_permission WHERE code = 'user:read');

INSERT INTO t_permission (code, name)
SELECT 'user:manage', '管理用户'
WHERE NOT EXISTS (SELECT 1 FROM t_permission WHERE code = 'user:manage');

-- 用业务编码查询关联主键，避免脚本依赖自增 ID；ADMIN 具备后续后台用户管理所需的最小权限。
INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
JOIN t_permission permission ON permission.code = 'user:manage'
WHERE role.code = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1
      FROM t_role_permission relation
      WHERE relation.role_id = role.id AND relation.permission_id = permission.id
  );
