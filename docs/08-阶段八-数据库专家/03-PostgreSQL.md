# PostgreSQL：从基础到资深进阶

> 所属：阶段八 数据库专家
> 定位：PostgreSQL 是**功能最全的开源关系型数据库**——在 MySQL 能力之上，多了 JSONB、窗口函数、地理（PostGIS）、丰富扩展。适合「既要关系型事务、又要 JSON/复杂分析/位置服务」的场景，常被当作 **MySQL 的功能更全替代**。**记住：绝大多数 OLTP 场景 PG 都够用，只有「纯追加、无关联、超海量写日志/时序」这类专用负载，才需要考虑专门的列存/时序库或 NoSQL。**

## 快速入门（能跑）

### 核心关键词速查
| 关键词 | 一句话大白话 | 例子 |
|-|-|-|
| 表 | 行 + 列的二维结构 | `CREATE TABLE user` |
| JSONB | 二进制 JSON 列，可索引可查询 | `data JSONB` |
| 窗口函数 | 分组内排名/取最新 | `ROW_NUMBER() OVER(PARTITION BY...)` |
| CTE | 一次查询的临时结果集 | `WITH t AS (SELECT...)` |
| 索引 | 加速查询 | GIN（JSON）/ B-tree（普通） |
| MVCC | 多版本并发控制 | 读不加锁 |
| VACUUM | 清理死元组 | 后台维护 |

### 最简可运行示例
```sql
-- 建表: 支持 JSONB + 数组 + 丰富类型
CREATE TABLE event_log (
    id      BIGSERIAL PRIMARY KEY,      -- 自增主键(BIGSERIAL)
    data    JSONB NOT NULL,             -- 半结构化数据, 可索引可查询
    tags    TEXT[],                     -- 数组类型
    geo     POINT,                      -- 内建几何类型(演示; 真用地理要 PostGIS 的 geometry(Point,4326))
    created_at TIMESTAMPTZ DEFAULT now()  -- 带时区时间戳(推荐)
);

-- 插入 JSON + 查询(用 @> 包含操作符, 走 GIN 索引)
INSERT INTO event_log (data) VALUES ('{"event":"page_view","uid":123}'::jsonb);
SELECT * FROM event_log WHERE data @> '{"event":"page_view"}'::jsonb;
```

> **代码备注（逐行解释）**：
> - `BIGSERIAL`：自增主键（等价 MySQL 的 AUTO_INCREMENT）。
> - `JSONB`：**已解析的二进制 JSON**——可对整个列直接建 GIN 索引、按内部字段高效查询。MySQL 的 JSON 也能查内部（`->`/`JSON_EXTRACT`），但要建生成列+二级索引或多值索引兜底，且数组包含查询支持较晚——**PG 的 JSONB 是对整列建 GIN，更省事**。
> - `text[]`：原生数组类型——PG 的亮点。
> - `TIMESTAMPTZ`：带时区的时间戳——比 MySQL 的 DATETIME 更适合跨时区业务。
> - `data @> '{"event":"page_view"}'`：**包含操作符**——JSON 里有没有这个字段值，走 GIN 索引。

## 核心概念

### 1. JSONB：半结构化数据的利器
```sql
-- JSONB 可对整个列建 GIN 索引, 按内部字段高效查询(比 MySQL 的生成列兜底更省事)
CREATE INDEX idx_event_data ON event_log USING GIN (data);   -- GIN 索引支持 @>/? 等
SELECT data->>'user_id' AS uid FROM event_log;               -- ->> 取文本
SELECT data->'event' FROM event_log;                         -- -> 取 JSON
```
> **该怎么做**：字段常变、不想频繁 ALTER 的场景用 JSONB（如埋点、配置、表单）。
> **不该怎么做**：JSONB 当普通关系用（那不如拆成规范列）；也别在 JSONB 里存必查但无索引的字段。

### 2. 窗口函数：一行 SQL 解难题
```sql
-- 取每个用户最近的一笔订单(MySQL 8+ 也有, PG 实现更完整)
SELECT * FROM (
    SELECT o.*,
           ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY created_at DESC) rn
    FROM "order" o
) t WHERE rn = 1;
```
> **该怎么做**：「分组内取最新一条」「分组排名」用窗口函数一行搞定——这是写出优雅 SQL 的关键。
> **不该怎么做**：用子查询或 app 层循环去实现「每组最新」——又慢又难维护。

### 3. CTE（Common Table Expression）
```sql
-- WITH 定义临时结果集, 让复杂查询可读
WITH recent_orders AS (
    SELECT user_id, COUNT(*) cnt FROM "order"
    WHERE created_at > now() - interval '7 days' GROUP BY user_id
)
SELECT u.id, u.name, COALESCE(r.cnt, 0) FROM user u LEFT JOIN recent_orders r USING(user_id);
```
> **该怎么做**：复杂查询用 `WITH` 拆分，可读性大幅提升。
> **不该怎么做**：大段嵌套子查询堆在一起——CTE 是更清晰的替代。

## 进阶

### 1. 索引类型
| 索引 | 适用 | 说明 |
|-|-|-|
| B-tree | 普通等值/范围 | 默认索引 |
| GIN | JSONB / 数组 | 支持 `@>`、`?` 包含查询 |
| GiST | 地理/范围 | PostGIS 用 |
| BRIN | 大表按序建 | 省空间 |
> **该怎么做**：JSONB 建 GIN；普通列建 B-tree；地理位置用 PostGIS 的 GiST。

### 2. MVCC 与 VACUUM（PG 特有）
```sql
-- MVCC 差异: PG 无回滚段, 旧版本元组留在堆表, 靠 VACUUM 清理
VACUUM (ANALYZE) event_log;    -- 清理死元组 + 更新统计信息
```
> **该怎么做**：定期 `VACUUM`（或开 autovacuum），避免表膨胀（dead tuples 堆积）。
> **不该怎么做**：长事务（`SELECT; 睡10分钟; COMMIT`）会阻断 VACUUM——连接池设 `idle_in_transaction_session_timeout` 上限。

### 3. 扩展（Extensions）——PG 的杀手锏
```sql
-- VECTOR 扩展: 做 RAG 向量检索(项目三用) —— 复用已有 PG, 不用单独上 Milvus
CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE docs (
    id      BIGSERIAL PRIMARY KEY,
    content TEXT,
    embedding vector(1536)                -- 1536 维向量(OpenAI 等模型输出维度, 按模型定)
);
CREATE INDEX idx_docs_embedding ON docs USING hnsw (embedding vector_cosine_ops);  -- HNSW 索引(pgvector ≥0.5.0 才支持)
-- 查询: 找最相似的 5 条(余弦相似度)
SELECT id, content, 1 - (embedding <=> :query_vec) AS similarity
FROM docs ORDER BY embedding <=> :query_vec LIMIT 5;

-- pg_trgm: 模糊搜索支持
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_user_name ON "user" USING gin (name gin_trgm_ops);   -- LIKE '%xx%' 也能走索引
```
> **代码备注（逐行解释）**：
> - `vector(n)`：PGVector 的向量列类型；`CREATE EXTENSION vector` 后可用。
> - `hnsw` 索引 + `vector_cosine_ops`：**HNSW 近似最近邻**索引，RAG 检索百万级向量也不慢。
> - `embedding <=> :query`：`<=>` 是**余弦距离操作符**，越小越相似——`ORDER BY ... LIMIT` 就是找最相似。
> - `gin_trgm_ops`：让 `LIKE '%xx%'` 这类模糊查询也能走索引（trigram 方案）。
> - **价值**：JSON、窗口、地理、向量都是 PG 扩展——**别为其中一个功能单独引入一个数据库**（先复用后新增原则）。

### 4. 性能优化：EXPLAIN ANALYZE
```sql
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM "order" WHERE user_id = 10086;
-- ANALYZE=真实执行; BUFFERS=看缓存命中情况(读了多少物理页)
```
> **该怎么做**：`EXPLAIN ANALYZE`（真实执行）比 `EXPLAIN`（预估）更能反映实际；`BUFFERS` 看缓存命中。
> **不该怎么做**：不看执行计划就猜——先测量再优化。

## 高可用：流复制 / 主从 / 热备

### 1. 流复制（Streaming Replication）
```ini
# 主库 postgresql.conf
wal_level = replica            # 开启 WAL 复制(默认 replica)
max_wal_senders = 5            # 允许几个备库连上来复制
```
```bash
# 备库: 用 pg_basebackup 从主库拉初始数据 + 配 standby
pg_basebackup -h 10.0.0.11 -U repl -D /var/lib/postgresql/standby -R
# 备库 postgresql.auto.conf 写入: primary_conninfo = 'host=10.0.0.11 port=5432 user=repl'
# 启动备库即开始流复制, SELECT pg_is_in_recovery() 看是否在恢复模式
```
> **代码备注（逐行解释）**：
> - `wal_level=replica`：主库开启 WAL 复制；`max_wal_senders` 允许备库连接。
> - `pg_basebackup`：把主库初始数据拉到备库；`-R` 自动生成 `standby.signal` + `primary_conninfo`。
> - 备库只读（`pg_is_in_recovery()=true`），可做读扩展 / 备份 / 高可用切换。
> - **PG 主从**：备库读只读，主故障切到备库——写仍是单点（与 MySQL 同理）。

### 2. 主从切换与高可用（Patroni）
```text
PG 主从自动切换: 常用 Patroni(基于 etcd/consul 选主) —— 主挂了自动切到备库
单库风险: 主库宕机即不可写, 必须配流复制 + 自动切换(Patroni)才谈得上高可用
```
> **该怎么做**：生产 PG 用**流复制 + Patroni 自动切换**（选主），主故障自动接管，数据不丢（WAL 同步）。
> **不该怎么做**：单 PG 裸奔——宕机即不可用。

### 3. 分区表（大表按时间拆）
```sql
-- PG 原生分区: 按时间范围分区, 查询自动"分区裁剪"(partition pruning)只扫对应分区
CREATE TABLE logs (
    id BIGINT, msg TEXT, created_at TIMESTAMPTZ
) PARTITION BY RANGE (created_at);                              -- 声明按范围分区
CREATE TABLE logs_202609 PARTITION OF logs FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE logs_202610 PARTITION OF logs FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
-- 查询: 只查 9 月的 SQL 会自动裁剪到 logs_202609, 其余分区不扫
SELECT * FROM logs WHERE created_at >= '2026-09-01' AND created_at < '2026-10-01';
```
> **该怎么做**：日志/流水按时间分区——查询走**分区裁剪**（`enable_partition_pruning` 默认开）；删历史数据用 `DROP TABLE logs_202609`（PG 没有 `DROP PARTITION` 语法）。
> **不该怎么做**：把分区当索引替代——分区是存储组织，索引是加速查询，配合使用。

## 场景与红线（怎么做 / 不该怎么做）

| 场景 | ✅ 该怎么做 | ❌ 不该怎么做 |
|-|-|-|
| 功能全的关系型 | PG（JSON/窗口/地理一个顶多个） | 遇到 JSON 就上 NoSQL |
| 埋点/配置/表单字段多变 | PG JSONB | 频繁 ALTER 加列（MySQL） |
| 分组取最新/排名 | 窗口函数 | app 循环处理 |
| 地理/位置服务 | PostGIS 扩展 | 手写经纬度计算（复杂） |
| RAG 向量检索 | PGVector（复用已有 PG） | 单独上 Milvus（重） |
| 极高频写入 | 超大规模点查/写入可用 Cassandra/HBase 等专用 NoSQL | 用 PG 硬扛高并发写入瓶颈 |

## 红线小结（必背）

1. **PG 是功能更全的替代选项**：JSONB/窗口/扩展/地理都比 MySQL 强，但**不是严格超集**——简单高频 OLTP 场景 MySQL 运维生态更成熟；按团队能力与场景取舍。
2. **JSONB 要建 GIN 索引**：否则包含查询还是慢。
3. **长事务是 PG 大忌**：阻断 VACUUM → 表膨胀 → 越来越慢。
4. **能用 PG 一个顶多个**：JSON、窗口、地理、向量都能做——**别为一个功能引入一个新数据库**（这是「先复用后新增」原则）。
5. **生产必须流复制 + 自动切换**：单 PG 裸奔宕机即不可用；用 Patroni 做选主。

## 进阶自测

- [ ] 能说清 JSONB 与 MySQL JSON 的差异（为何 PG 能建 GIN 高效查）
- [ ] 能用窗口函数写出「每组最新一条」并解释 `PARTITION BY`
- [ ] 能用 `WITH` CTE 拆分一个复杂查询
- [ ] 能说清 MVCC 差异（无回滚段）与 VACUUM 的作用、长事务危害
- [ ] 能用 PGVector + HNSW 实现 RAG 向量检索
- [ ] 能搭流复制（wal_level + pg_basebackup）并理解 Patroni 自动切换
- [ ] 能列出 PG 常用扩展（PostGIS/PGVector/pg_trgm）及各自适用场景
