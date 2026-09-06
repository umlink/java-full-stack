# Elasticsearch：从基础到资深进阶

> 所属：阶段八 数据库专家
> 定位：Elasticsearch 是**全文搜索引擎 + 日志/分析引擎**，核心是倒排索引 + 分词 + 聚合。适合「搜索、日志分析、复杂聚合」场景。**记住一条红线：ES 是检索加速层，不是 OLTP 主库**——业务真相存 MySQL/PG，ES 只做搜索和分析。

## 快速入门（能跑）

### 核心关键词速查
| 关键词 | 一句话大白话 | 例子 |
|-|-|-|
| 索引（Index） | ≈ 数据库表 | `products` |
| 文档（Document） | ≈ 一行记录 | 一个商品 |
| 分片（Shard） | 索引的并行单元 | 一个索引拆多个分片 |
| 倒排索引 | 词 → 文档 | 搜"手机"找到相关商品 |
| 分词 | 把文本切成词 | `ik_max_word` |
| DSL | ES 的查询语法（JSON） | `match`/`bool` 查询 |
| 聚合 | 分组统计 | 按价格区间统计 |

### 最简可运行示例
```java
// 用 Elasticsearch Java Client 查询(带详细注释)
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;

@Service
public class ProductSearch {
    private final ElasticsearchClient client;   // ES 客户端(自动注入)

    public ProductSearch(ElasticsearchClient client) { this.client = client; }

    public SearchResponse<Product> search(String keyword) throws Exception {
        return client.search(s -> s
            .index("products")                    // 查哪个索引(≈ 表)
            .query(q -> q.bool(b -> b             // bool 查询: 组合条件(≈ WHERE)
                .must(m -> m.match(mq -> mq.field("name").query(keyword)))  // 全文匹配 name
                .filter(f -> f.term(t -> t.field("status").value("1")))      // 精确 filter status=1（keyword 字段用字符串）
            )),
            Product.class);                       // 反序列化成 Product 对象
    }
}
```

> **代码备注（逐行解释）**：
> - `client.search(...)`：发起搜索请求。
> - `.index("products")`：指定在哪个「索引」里搜（≈表）。
> - `.bool(...)`：**bool 查询**用来组合条件；`must`（必须满足，算分≈AND）、`filter`（精确过滤，不算分）。
> - `.match(field("name").query(keyword))`：**全文匹配**——按分词找，智能匹配同义词/词形。
> - `.term(field("status").value("1"))`：**精确匹配**（≈`WHERE status='1'`），走 filter 不算分更快；`keyword` 字段要传字符串。
> - 对比 SQL：ES 用 JSON DSL 表达「过滤+全文检索+排序+聚合」，在复杂搜索上远比 `LIKE` 强大。

## 核心概念

### 1. 倒排索引与分词
```text
-- 倒排索引: "词 → 文档列表", 反过来了
CREATE INDEX idx_goods_name ON goods (name);  -- MySQL 的索引是"列→行"
-- ES 相反: 把 "[苹果手机] [华为手机]" 切词 → "苹果"→doc1, "手机"→doc1,doc2
```
| 分词器 | 用途 | 说明 |
|-|-|-|
| `ik_max_word` | 最细粒度切分 | 索引端收录词最全，避免漏搜 |
| `ik_smart` | 智能切分 | 更精准，词更少 |
> **该怎么做**：中文用 IK 分词，**索引端与搜索端保持一致**（最稳）——否则两端分词粒度不同，查询词切出来的 token 对不上索引里已有的 token，会「漏搜/误搜」。常见组合：① 索引 `ik_max_word` + 搜索 `ik_smart`（收录全、查询准，但需理解粒度差异）；② 两端统一用 `ik_max_word`（最不易踩坑）。`text` 字段做全文、`keyword` 字段做精确/排序。

### 2. 索引与数据结构（mapping）
```json
PUT /products
{
  "mappings": {
    "properties": {
      "name": { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },   // 索引用 ik_max_word、搜索用 ik_smart（见上）
      "price": { "type": "double" },
      "status": { "type": "keyword" },                          // 精确匹配/排序
      "created_at": { "type": "date" }
    }
  }
}
```
> **该怎么做**：`text` 用于全文检索，`keyword` 用于精确过滤/排序/聚合——**一个字段可能需要双字段**（如 `name` 全文 + `name.keyword` 排序）。`search_analyzer` 单独指定搜索端分词器，若不写则默认等于 `analyzer`。
> **不该怎么做**：把所有字段都设 `text`——精确匹配会变成全文匹配，排序也出问题。

## 进阶

### 1. 深分页问题（ES 的头号坑）
```text
-- from+size 翻页很深: 每分片取满 from+size 条再丢弃 → 慢
-- 第 1000 页(size=10, from=9990) → 每分片取 10000 条; 且 max_result_window=10000 封死
SELECT * FROM products LIMIT 9990, 10;  -- (MySQL 的类比)
```
| 翻页方式 | 适用 | 说明 |
|-|-|-|
| `from+size` | 浅分页（<1w） | 深了会爆 |
| `search_after` | 深分页/滚动加载 | 用上一页最后一条的排序值做游标，恒定成本 |
| PIT + `search_after` | 深分页 + 一致性快照/全量导出 | **PIT 不是翻页方式**，它只锁住索引快照防滚动中数据变；必须配 `search_after` 使用 |
> **该怎么做**：深翻页用 `search_after`（游标式）；全量导出用 **PIT + `search_after`**（PIT 保一致快照，避免导出中途索引被改；`scroll` 已不推荐）。
> **不该怎么做**：`from=100000` 硬翻——每个分片取 10 万条再丢，直接拖垮。

### 2. 聚合（分析统计）
```json
GET /orders/_search
{
  "size": 0,
  "aggs": {
    "by_status": { "terms": { "field": "status" } },        // 按状态分组统计
    "avg_amount": { "avg": { "field": "amount" } }          // 平均金额
  }
}
```
> **该怎么做**：报表/统计用 ES `aggs` 聚合（快），别拉全量到内存算。
> **不该怎么做**：`size` 不加 `0`（默认返回 10 条命中，浪费）。

### 3. 数据同步（MySQL → ES）
| 方案 | 方式 | 适用 |
|-|-|-|
| 双写 | 业务代码同时写 MySQL 和 ES | 简单，但要处理失败 |
| MQ 异步 | 写 MySQL 后发消息，消费端同步 ES | 解耦，推荐 |
| Canal | 订阅 binlog 自动同步 | 无侵入，但要部署 |
> **该怎么做**：用 MQ 异步同步（体验好 + 解耦）；严格一致场景可双写 + 对账补偿。
> **不该怎么做**：ES 当主库——它**没有 ACID 事务**且是**准实时**（默认 `refresh_interval=1s`，写入后约 1 秒才可搜索到；可调 `refresh` API 强制刷新），读你刚写入的可能还没刷出来。

### 4. 索引模板与别名（企业级惯用法）
```json
// 索引模板: 自动给新索引套上统一的 mapping/setting —— 日志按天建索引时必备
PUT /_index_template/logs
{
  "index_patterns": [ "logs-*" ],                       // 匹配 logs-开头的索引名
  "template": {
    "settings": { "number_of_shards": 3, "number_of_replicas": 1 },
    "mappings": {
      "properties": {
        "message": { "type": "text", "analyzer": "ik_max_word" },
        "level":   { "type": "keyword" }
      }
    }
  }
}

// 别名: 优雅地切换索引(读写走 alias, 背后索引可换)
POST /_aliases
{ "actions": [ { "add":  { "index": "logs-2026-09", "alias": "logs-write" } } ] }
```
> **代码备注（逐行解释）**：
> - **索引模板**：`index_patterns` 匹配 `logs-*` 就自动套用 mapping/settings——新索引不用手动配。
> - `number_of_shards`：主分片数（并行/容量上限），一次定够；`number_of_replicas`：副本数（高可用 + 读）。
> - **别名（alias）**：应用层「读写走 `logs-write` 这个别名」，背后索引可无缝切换（升级/重建不中断）。
> - 组合使用：按天建 `logs-2026-09`，模板统一 mapping，读写走别名，ILM 管理生命周期。

### 5. 集群与性能（分片/副本/高可用）
- **主分片（primary shard）**：数据的实际存储单元，并行查询的粒度——**一次定够，多了反而慢（跨分片聚合）**。
- **副本分片（replica）**：主分片的副本——**高可用（主挂了副本顶上）+ 读扩展**。
- **集群角色**：`node.roles` 可配置 `master`（管元数据）/ `data`（存数据）/ `ingest`（预处理）/ `ml` 等——**大集群分离部署**（master 与 data 分开）。**纯协调节点（coordinating）= `node.roles: []` 空数组**，它不存数据、只接收并分发请求；「coordinating」不是 `node.roles` 里的合法值。
- **索引生命周期（ILM）**：日志按天/月建索引，配 ILM 做 hot→warm→cold 冷热分层 + 定期 delete——**无限增长的单一大索引是事故源**。
- **快照备份**：定期 `snapshot` 到对象存储（S3/MinIO），防数据丢失。

```json
// 查看集群健康(分片是否 all 分配)
GET /_cluster/health
{ "status": "green", "number_of_nodes": 3, ... }   // green=主分片+副本都就绪
```
> **该怎么做**：分片数一次定够；副本 ≥1 保高可用；日志类配 ILM 冷热分层。
> **不该怎么做**：主分片设太多（每个查询跨过多分片，聚合慢）；单节点无副本。

## 场景与红线（怎么做 / 不该怎么做）

| 场景 | ✅ 该怎么做 | ❌ 不该怎么做 |
|-|-|-|
| 商品/文档全文搜索 | ES（倒排索引+分词） | MySQL `LIKE %xx%` 硬扛 |
| 日志分析 | ES 索引 + 冷热分层（ILM） | 日志塞 MySQL |
| 复杂聚合统计 | ES `aggs` | 拉全量到内存算 |
| 核心交易数据 | MySQL（唯一事实源） | ES 当主库（无事务、准实时） |
| 数据量小无分词 | MySQL LIKE + 索引就够 | 为「显得专业」上 ES |

## 红线小结（必背）

1. **ES 是检索/分析引擎**：核心业务数据存 MySQL/PG，ES 只做搜索和聚合。
2. **`text` 与 `keyword` 分清**：全文用 text，精确/排序/聚合用 keyword。
3. **深分页用 `search_after`**：别 `from` 硬翻。
4. **中文用 IK 分词**：默认分词对中文不友好。
5. **数据同步用 MQ 异步**：最终一致 + 对账补偿。
6. **分片数一次定够、副本 ≥1**：主分片多了聚合慢；副本保高可用。
7. **日志用索引模板 + 别名 + ILM**：统一 mapping、优雅切换、冷热分层。
8. **什么时候才上 ES**：数据量大 + 有分词/全文/聚合需求；数据小无分词需求 → MySQL 够用，别过度设计。

## 进阶自测

- [ ] 能说清倒排索引与 MySQL B+ 树索引的本质区别
- [ ] 能写出一个 `bool` 查询（must + filter）并解释 match/term 差异
- [ ] 能说清 `text` vs `keyword` 的适用场景与双字段做法
- [ ] 能解释深分页为什么慢、为什么 `search_after` 能解决
- [ ] 能用索引模板统一日志索引 + 别名切换 + ILM 冷热分层
- [ ] 能说清主分片/副本分片的作用，以及分片数为何一次定够
- [ ] 能设计 MySQL→ES 的同步方案（MQ 异步 + 对账补偿）
- [ ] 能说出「什么时候才该上 ES、什么时候 MySQL 够用」
