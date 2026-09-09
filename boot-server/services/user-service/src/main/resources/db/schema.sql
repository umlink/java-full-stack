-- ============================================================
-- 初始化脚本：随仓库提交 —— clone 后应用首次启动即自动建表 + 灌初始数据
-- 幂等设计：
--   1. CREATE TABLE IF NOT EXISTS —— 重复执行不报错
--   2. 初始数据仅在「表为空」时插入（NOT EXISTS 守卫）——
--      之后你的增删改全部保留，重启不会把实验数据重置回初始状态
-- ============================================================

CREATE TABLE IF NOT EXISTS t_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL,
    email       VARCHAR(128) NOT NULL,
    age         INT          NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

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
