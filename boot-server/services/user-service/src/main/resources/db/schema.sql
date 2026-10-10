-- ============================================================
-- 初始化脚本：随仓库提交 —— clone 后应用首次启动即自动建表 + 灌初始数据
-- 幂等设计：
--   1. CREATE TABLE IF NOT EXISTS —— 重复执行不报错
--   2. 初始数据仅在「表为空」时插入（NOT EXISTS 守卫）——
--      之后你的增删改全部保留，重启不会把实验数据重置回初始状态
--   3. 商品域种子按稳定业务键逐条守卫，不覆盖已有商品或 SKU
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

INSERT INTO t_permission (code, name)
SELECT 'product:manage', '管理商品'
WHERE NOT EXISTS (SELECT 1 FROM t_permission WHERE code = 'product:manage');

INSERT INTO t_permission (code, name)
SELECT 'order:manage', '管理订单'
WHERE NOT EXISTS (SELECT 1 FROM t_permission WHERE code = 'order:manage');

-- 用业务编码查询关联主键，避免脚本依赖自增 ID；ADMIN 关联各后台资源所需权限。
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

INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
JOIN t_permission permission ON permission.code = 'product:manage'
WHERE role.code = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1
      FROM t_role_permission relation
      WHERE relation.role_id = role.id AND relation.permission_id = permission.id
  );

INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
JOIN t_permission permission ON permission.code = 'order:manage'
WHERE role.code = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1
      FROM t_role_permission relation
      WHERE relation.role_id = role.id AND relation.permission_id = permission.id
  );

-- 商品状态由数据库约束兜底；逻辑删除不改变原记录的业务身份。
CREATE TABLE IF NOT EXISTS t_product (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(1024) NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    deleted     TINYINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_product_status CHECK (status IN ('DRAFT', 'ON_SALE', 'OFF_SALE')),
    CONSTRAINT ck_product_deleted CHECK (deleted IN (0, 1))
);

-- 外键默认 RESTRICT：商品仍有 SKU 时禁止物理删除；sku_code 不包含 deleted，删除后也不能复用。
CREATE TABLE IF NOT EXISTS t_sku (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id  BIGINT NOT NULL,
    sku_code    VARCHAR(64) NOT NULL,
    price       DECIMAL(19, 2) NOT NULL,
    version     INT NOT NULL DEFAULT 0,
    deleted     TINYINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sku_product FOREIGN KEY (product_id) REFERENCES t_product (id),
    CONSTRAINT uk_sku_code UNIQUE (sku_code),
    CONSTRAINT ck_sku_price CHECK (price >= 0),
    CONSTRAINT ck_sku_version CHECK (version >= 0),
    CONSTRAINT ck_sku_deleted CHECK (deleted IN (0, 1))
);

CREATE INDEX IF NOT EXISTS idx_sku_product_id ON t_sku (product_id);

-- 一个 SKU 对应一行库存；未配置库存的 SKU 不预建记录，首次设置时插入。
-- 外键默认 RESTRICT，SKU 仍有库存记录时禁止物理删除；条件更新与检查约束共同守住可用量非负。
CREATE TABLE IF NOT EXISTS t_stock (
    sku_id       BIGINT PRIMARY KEY,
    total_count  BIGINT NOT NULL DEFAULT 0,
    locked_count BIGINT NOT NULL DEFAULT 0,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_sku FOREIGN KEY (sku_id) REFERENCES t_sku (id),
    CONSTRAINT ck_stock_counts CHECK (total_count >= 0 AND locked_count >= 0 AND locked_count <= total_count)
);

-- 购物车以用户 + SKU 为唯一业务身份；用户/SKU 的物理删除受外键限制，逻辑删除不清空学习数据。
CREATE TABLE IF NOT EXISTS t_cart_item (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    sku_id      BIGINT NOT NULL,
    quantity    INT NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cart_item_user FOREIGN KEY (user_id) REFERENCES t_user (id),
    CONSTRAINT fk_cart_item_sku FOREIGN KEY (sku_id) REFERENCES t_sku (id),
    CONSTRAINT uk_cart_item_user_sku UNIQUE (user_id, sku_id),
    CONSTRAINT ck_cart_item_quantity CHECK (quantity BETWEEN 1 AND 999)
);

-- 订单号独立于数据库主键；订单创建时的价格和金额均为不可回算的快照。
CREATE TABLE IF NOT EXISTS t_order (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no     VARCHAR(32) NOT NULL,
    user_id      BIGINT NOT NULL,
    status       VARCHAR(16) NOT NULL DEFAULT 'CREATED',
    total_amount DECIMAL(19, 2) NOT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_order_no UNIQUE (order_no),
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES t_user (id),
    CONSTRAINT ck_order_status CHECK (status IN ('CREATED', 'PAID', 'SHIPPED', 'DONE', 'CANCELLED')),
    CONSTRAINT ck_order_amount CHECK (total_amount >= 0)
);
CREATE INDEX IF NOT EXISTS idx_order_user_time ON t_order (user_id, create_time, id);

-- 旧学习订单没有幂等键，保留为 NULL；新订单始终同时写入键和指纹。
-- H2 唯一约束允许多个 NULL，因而不会改写或阻断已有学习数据。
ALTER TABLE t_order ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(64) NULL;
ALTER TABLE t_order ADD COLUMN IF NOT EXISTS request_fingerprint CHAR(64) NULL;
ALTER TABLE t_order ADD CONSTRAINT IF NOT EXISTS uk_order_user_idempotency UNIQUE (user_id, idempotency_key);
ALTER TABLE t_order ADD CONSTRAINT IF NOT EXISTS ck_order_idempotency_pair CHECK (
    (idempotency_key IS NULL AND request_fingerprint IS NULL)
    OR (idempotency_key IS NOT NULL AND request_fingerprint IS NOT NULL)
);

CREATE TABLE IF NOT EXISTS t_order_item (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    sku_id   BIGINT NOT NULL,
    quantity INT NOT NULL,
    price    DECIMAL(19, 2) NOT NULL,
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES t_order (id),
    CONSTRAINT fk_order_item_sku FOREIGN KEY (sku_id) REFERENCES t_sku (id),
    CONSTRAINT uk_order_item_sku UNIQUE (order_id, sku_id),
    CONSTRAINT ck_order_item_quantity CHECK (quantity BETWEEN 1 AND 999),
    CONSTRAINT ck_order_item_price CHECK (price >= 0)
);

-- 订单与超时任务在同一本地事务落库；MQ 发送结果只推进 Outbox 状态。
CREATE TABLE IF NOT EXISTS t_order_timeout_outbox (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id          CHAR(36) NOT NULL,
    business_key      VARCHAR(96) NOT NULL,
    order_id          BIGINT NOT NULL,
    event_version     INT NOT NULL,
    payload           VARCHAR(1024) NOT NULL,
    status            VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    send_attempts     INT NOT NULL DEFAULT 0,
    next_attempt_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at           TIMESTAMP NULL,
    processed_at      TIMESTAMP NULL,
    last_error        VARCHAR(512) NULL,
    create_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_order_timeout_event UNIQUE (event_id),
    CONSTRAINT uk_order_timeout_business UNIQUE (business_key),
    CONSTRAINT uk_order_timeout_order UNIQUE (order_id),
    CONSTRAINT fk_order_timeout_order FOREIGN KEY (order_id) REFERENCES t_order (id),
    CONSTRAINT ck_order_timeout_version CHECK (event_version = 1),
    CONSTRAINT ck_order_timeout_status CHECK (
        status IN ('PENDING', 'SENDING', 'SENT', 'CONSUMING', 'PROCESSED', 'IGNORED', 'DEAD')
    ),
    CONSTRAINT ck_order_timeout_attempts CHECK (send_attempts >= 0)
);
CREATE INDEX IF NOT EXISTS idx_order_timeout_dispatch
    ON t_order_timeout_outbox (status, next_attempt_time, id);

-- 同一订单至多一张成功支付单；支付流水号只用于外部追踪。
CREATE TABLE IF NOT EXISTS t_payment (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id    BIGINT NOT NULL,
    pay_no      VARCHAR(40) NOT NULL,
    amount      DECIMAL(19, 2) NOT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_payment_order UNIQUE (order_id),
    CONSTRAINT uk_payment_no UNIQUE (pay_no),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES t_order (id),
    CONSTRAINT ck_payment_amount CHECK (amount >= 0),
    CONSTRAINT ck_payment_status CHECK (status IN ('SUCCESS'))
);

INSERT INTO t_product (name, description, status)
SELECT 'BootMall 入门手册', '用于验证商品与 SKU 数据模型', 'ON_SALE'
WHERE NOT EXISTS (SELECT 1 FROM t_product WHERE name = 'BootMall 入门手册');

INSERT INTO t_product (name, description, status)
SELECT 'BootMall 练习本', '用于验证多规格 SKU', 'ON_SALE'
WHERE NOT EXISTS (SELECT 1 FROM t_product WHERE name = 'BootMall 练习本');

INSERT INTO t_sku (product_id, sku_code, price)
SELECT product.id, 'BOOTMALL-BOOK-STD', 29.90
FROM t_product product
WHERE product.id = (SELECT MIN(id) FROM t_product WHERE name = 'BootMall 入门手册')
  AND NOT EXISTS (SELECT 1 FROM t_sku WHERE sku_code = 'BOOTMALL-BOOK-STD');

INSERT INTO t_sku (product_id, sku_code, price)
SELECT product.id, 'BOOTMALL-NOTE-A5', 9.90
FROM t_product product
WHERE product.id = (SELECT MIN(id) FROM t_product WHERE name = 'BootMall 练习本')
  AND NOT EXISTS (SELECT 1 FROM t_sku WHERE sku_code = 'BOOTMALL-NOTE-A5');

INSERT INTO t_sku (product_id, sku_code, price)
SELECT product.id, 'BOOTMALL-NOTE-A4', 12.90
FROM t_product product
WHERE product.id = (SELECT MIN(id) FROM t_product WHERE name = 'BootMall 练习本')
  AND NOT EXISTS (SELECT 1 FROM t_sku WHERE sku_code = 'BOOTMALL-NOTE-A4');
