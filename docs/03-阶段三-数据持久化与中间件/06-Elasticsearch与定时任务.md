# 阶段三 · 小点 6：Elasticsearch 与定时任务

> 所属：阶段三 数据持久化与中间件
> 定位：搜索与调度是数据层的「最后一公里」——复杂检索从 DB 卸载到 ES，周期任务从单点脚本升级到调度平台。两者都是「看起来简单、上线才见真章」的组件。

## 快速入门

> 本节为「搜索与调度速览」：先认识 ES 解决什么问题、XXL-Job 解决什么问题；「倒排索引、数据同步」留在正文提高部分。

### 本讲核心关键词速查

| 关键词 | 一句话大白话 | 例子 |
|-|-|-|
| Elasticsearch（ES） | 专门做全文搜索的高性能引擎 | 商品搜索 |
| 倒排索引 | 「词 → 文档」的索引结构 | 搜"手机"找到相关商品 |
| 分词 | 把文本切成词 | 中文分词 `ik` |
| DSL | ES 的查询语法（JSON） | `match` / `bool` 查询 |
| 深分页 | 翻页很深很慢的问题 | 第 10000 页 |
| 定时任务 | 周期性执行的任务 | 每晚清理数据 |
| XXL-Job | 分布式调度平台 | 分片广播、失败重试 |
| 数据同步 | MySQL 与 ES 保持一致 | 下单后同步到 ES |

### 本讲在解决什么问题

- **问题**：① 复杂搜索/全文检索用 DB 的 `LIKE` 很慢，用 ES 更快；② 有些任务要定时执行，单点脚本不可靠，要分布式调度平台。
- **你要带走的一句话**：**搜索需求上 ES**（倒排索引 + 分词），但 ES 有集群运维成本——数据量小、无分词需求时 MySQL LIKE + 索引够用。**定时任务用 XXL-Job**（分片广播、失败重试），别用单点脚本。

### 最简可运行示例（照抄能跑）

```java
// ES 查询: 用 bool 查询组合条件(与 SQL WHERE 对应)
import co.elastic.clients.elasticsearch.ElasticsearchClient;

SearchResponse<Doc> resp = client.search(s -> s
    .index("products")                    // 查哪个索引(≈ 表)
    .query(q -> q.bool(b -> b             // bool 查询: 组合多个条件(≈ WHERE)
        .must(m -> m.match(mq -> mq.field("name").query("手机")))   // 名称含"手机"(≈ LIKE)
        .filter(f -> f.term(t -> t.field("status").value(1)))        // status=1(≈ 精确过滤)
    )),
    Doc.class);
```

> 代码备注（逐行解释）：
> - `client.search(...)`：发起一次搜索请求。
> - `.index("products")`：指定在哪个「索引」（相当于数据库表）里搜。
> - `.bool(...)`：**bool 查询**用来组合多个条件，`must` 必须满足（≈ AND），`filter` 精确过滤（不算分）。
> - `.match(field("name").query("手机"))`：**全文匹配**——按分词在 name 字段找「手机」（≈ `LIKE %手机%` 但更智能）。
> - `.term(field("status").value(1))`：**精确匹配** status=1（≈ `WHERE status=1`）。
> - 对比 SQL：ES 用 JSON DSL 表达「过滤 + 全文检索 + 排序 + 聚合」，在复杂搜索上远比 `LIKE` 强大。

### 关键概念说明

| 概念 | 干什么 | 最易踩的坑 |
|-|-|-|
| 倒排索引 | 词→文档的索引 | 全文检索地基；分词质量影响结果 |
| 索引（Index） | ≈ 数据库表 | 日志会无限增大，按天/月建 + ILM 冷热 |
| bool 查询 | 组合条件 | `must`(算分)/`filter`(不算分)/`should`/`must_not` |
| 深分页 | 很深的翻页 | 别硬翻，用 `search_after` |
| 数据同步 | MySQL→ES | 双写/MQ/Canal；要对账补偿 |
| XXL-Job | 分布式调度 | 分片广播、失败重试、告警 |
| 集群角色 | master/data/coordinating | 大集群三角色分离部署 |

### 常用约定 / 命名提示

- **什么情况才上 ES**：数据量大且有分词/全文检索/聚合需求；数据小、无分词需求 → MySQL LIKE + 索引够用，别为「显得专业」上 ES。
- **ES ≠ 数据库**：metadata 过滤弱、更新有延迟——业务真相进 MySQL/PG，ES 是检索加速层。
- **定时任务用 XXL-Job**：别用 Spring `@Scheduled` 硬扛分布式（多实例会重复执行）；分片广播 + 失败重试 + 告警。

## 精简大纲

1. 倒排索引原理与中文分词
2. DSL 查询与聚合分析
3. 与 MySQL 的数据同步、深分页问题
4. 定时任务：Spring Scheduler 的局限与 XXL-Job

## 学习内容详情

### 1. 倒排索引与分词

**倒排索引**：正排是「文档 → 词」，倒排是「词 → 文档列表」——全文检索的本质是**从词反查文档**。

```json
// 文档: {"id":1, "title":"Java 虚拟机 实战"}   {"id":2, "title":"Java 编程思想"}
// 分词后建倒排:
{
  "java":        [1, 2],
  "虚拟机":      [1],
  "实战":        [1],
  "编程":        [1, 2],
  "思想":        [2]
}
// 搜 "Java 实战": 两个词 Posting List 求交/并 → 命中 1(两词都有) 与 2(仅 java), 按相关性打分排序
```

- 结构三层：Term Dictionary（词表，FST 压缩前缀树）→ Posting List（文档 ID + 词频 + 位置）→ Skip List 加速求交。
- **中文必须分词**：IK 分析器两种模式——`ik_max_word`（最细粒度，**建索引用**）/ `ik_smart`（粗粒度，**查询用**）。索引与查询用同一分词器是检索质量的地基，错配会出现「明明有却搜不到」。

```bash
curl -X PUT "localhost:9200/product" -H 'Content-Type: application/json' -d '{
  "mappings": {
    "properties": {
      "title":   { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
      "price":   { "type": "scaled_float", "scaling_factor": 100 },
      "tags":    { "type": "keyword" },              // ⚠️ 精确过滤/聚合用 keyword, text 会被分词毁掉
      "createdAt": { "type": "date" }
    }
  }
}'
```

- **索引生命周期**：日志 / 订单类搜索数据按天或按月建索引，配 ILM（索引生命周期管理）做冷热分层与定期删除——无限增长的单一大索引是事故源。

### 2. DSL 查询与聚合

```json
// bool 组合: must(参与打分) / filter(不算分+可缓存) / should(或) / must_not
GET /product/_search
{
  "query": {
    "bool": {
      "must":   [ { "match": { "title": "Java 实战" } } ],          // 分词匹配
      "filter": [                                                    // 结构化条件一律放 filter —— 免打分, 可缓存
        { "range": { "price": { "gte": 30, "lte": 100 } } },
        { "term":  { "tags": "编程" } }                             // keyword 精确值
      ]
    }
  },
  "highlight": { "fields": { "title": {} } }                        // 命中片段高亮
}
```

```json
// 聚合: metrics + bucket 嵌套 —— 类 GROUP BY 但能多层
GET /order/_search
{
  "size": 0,                                                         // 不要命中文档, 只要统计
  "aggs": {
    "per_day": {
      "date_histogram": { "field": "createdAt", "calendar_interval": "day" },   // bucket: 按天分桶
      "aggs": { "amt_p99": { "percentiles": { "values": [50, 99] } } }           // 嵌套 metrics: 每天金额中位/p99
    },
    "avg_all": { "avg": { "field": "amount" } }
  }
}
```

- **相关性打分（BM25）**：词频饱和 + 逆文档频率 + 字段长度归一——比 TF-IDF 多了「词频高到一定程度不再加分」的防堆砌；默认理解即可，搜索体验差先查分词与字段权重，不是调 BM25 参数。
- `match`（分词后匹配，用于 text）vs `term`（不分词精确值，用于 keyword）——**用错是新手第一大坑**：对 text 字段 term 查一个词往往查不到（索引里存的是分词后的词元）。

### 3. 数据同步与深分页

```text
MySQL → ES 同步方案对比:
  双写(DB 写完再写 ES): 简单但强耦合 —— ES 抖动拖慢写链路, 且失败后补写麻烦
  MQ 异步(写 DB 发事件, 消费者写 ES): 解耦可重试 —— 秒级最终一致
  Canal 订阅 binlog: 与业务代码零侵入, 链路见阶段三全景图 —— 存量库无埋点的最佳解
  三者共性: 消费端要幂等(同文档重复写覆盖无害, 天然幂等) + 失败进重试/DLQ + 定期对账补偿
```

- **深分页问题**：`from + size` 要取回「前 from+size 条」再丢弃——第 1000 页意味着每个分片都要交 10020 条候选。ES 默认 `index.max_result_window=10000` 直接封死。
- 解法：`search_after`（游标式，上一页最后一条的排序值作起点，翻页恒定成本）；全量导出用 PIT + search_after（scroll 已不推荐）：

```json
GET /order/_search
{ "size": 20, "sort": [{"createdAt": "desc"}, {"_id": "asc"}],
  "search_after": ["2026-09-05T10:00:00Z", "cursor-ocid-117"] }
```

### 4. 定时任务

```java
// Spring Scheduler 的适用边界: 单实例、轻量任务
@Component
class ReportTask {
    @Scheduled(cron = "0 30 2 * * ?")       // 每天 02:30 —— 注意是 cron 表达式(6 段, 多一个秒)
    public void dailyReport() { /* 发日报 */ }

    // 局限暴露点:
    // ① 多实例部署 → 三台机器各跑一遍(重复发日报): 要分布式锁挡
    // ② 上次跑挂/跑超时 → 无重试无告警无记录: 要自建账
    // ③ 任务互相抢线程池 → 一个慢任务饿死全部
}
```

- **XXL-Job 补齐**：管理台可视化调度（cron 在线改）、**分片广播**（`shardIndex/shardTotal` 把大任务拆到多实例并行，如按用户 ID 取模分片跑对账）、失败重试 + 告警、手动触发与执行日志——把定时任务从「脚本」升级成「有运维面的服务」。

```java
@Component
public class OrderReconcileJob {
    @XxlJob("orderReconcile")                       // 注册到 XXL-Job 执行器
    public void run() {
        int idx = XxlJobHelper.getShardIndex();    // 我是第几片
        int total = XxlJobHelper.getShardTotal();  // 一共几片
        // 分片广播: 每实例处理 id % total == idx 的订单 —— 水平扩展并行度
        orderService.reconcileByShard(idx, total);
    }
}
```

- **时间轮**：Netty / Kafka 内部的定时结构——环形数组 + 指针按 tick 走动，任务挂格子里；海量短周期定时的 O(1) 实现（JDK `DelayQueue` 是优先队列 O(log n) 对照）。原理认知即可。

### 坑点提醒

- **mapping 里 text/keyword 用反**：聚合 text 字段直接报错或烧 fielddata（打开就是内存炸弹）——**需要排序 / 聚合的字段一律 keyword（或 multi-field 同时两种）**。
- **Canal 断点不续传**：binlog 位点丢失 = 全量重导或数据空洞——位点持久化 + 定期全量对账兜底。
- **`@Scheduled` 方法抛异常被吞**：任务静默停摆直到人肉发现——cron 任务第一行 try-catch 全包裹 + 上报；更优解直接上 XXL-Job。
- **ES 当主存储用**：近实时（refresh 1s）+ 无事务——**DB 永远是 OLTP 真相，ES 是只读视图**。
- **数据量小就别上 ES**：百万以下且无分词需求 → MySQL LIKE + 索引 / 全文索引够用；ES 换来搜索能力的同时引入了集群运维成本（分片、副本、调优）——别为了「显得专业」上 ES。
- **快照备份要有**：snapshot API 定期把索引备到远端仓库——ES 误删 / 索引损坏没有「恢复出厂」，没快照就是真没了。
- **集群角色分工**：master 管元数据、data 存数据、coordinating 只做协调（接查询、归并结果）——大集群三角色分离部署。

## 本节自检

- [ ] 能解释倒排索引的结构，以及为什么 filter 比 must 快
- [ ] 能区分 `match` 与 `term`，并说出 text/keyword 的选择依据
- [ ] 能给出「MySQL → ES 同步」的两种方案并说明 Canal + MQ 为什么优于双写
- [ ] 能说出深分页为什么慢、search_after 怎么解决
- [ ] 能列举 Spring Scheduler 的三个局限及 XXL-Job 分别怎么补

## 本节配套思考题

1. 商品搜索要「标题模糊 + 价格区间 + 品牌过滤 + 按销量排序」——哪些字段进 must / filter / sort？mapping 分别定成什么类型？
2. 对账任务用 XXL-Job 分片广播，实例数从 3 扩到 5 的**运行中扩容**会发生什么（分片重分配瞬间）？怎么设计幂等让它不出错？
3. ES 的 refresh_interval（1s 近实时）与「写入后立即可搜」的矛盾——搜索结果页能忍，「我刚发的帖子呢」不能忍；给两种业务分别的读写策略。
