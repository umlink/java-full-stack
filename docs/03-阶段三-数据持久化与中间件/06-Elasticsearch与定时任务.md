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

先对齐 ES 的概念坐标系——它把「数据库那套」换了个名字（索引≈表、文档≈行、字段≈列、mapping≈建表语句），对照起来读正文不迷路：

> **生活版——同一个东西两套叫法**：前端项目里「路由表 / 页面 / 组件树」是同一套东西的不同角度；ES 与 MySQL 也是「同一份数据、两套命名」——存的内容一样，服务的目标完全不同（MySQL 管事务真相，ES 管检索速度）。
>
> **换成 ES**：MySQL 的表（table）对应 ES 的**索引（Index）**，一行记录对应一个**文档（Document）**，字段（column）对应**字段（field）**，`CREATE TABLE` 对应 **mapping（字段类型映射）**。索引（Index）、文档（Document）都是术语首现，先记这一层对应，后面细看。

```mermaid
flowchart LR
    A["MySQL 表<br/>如 product"] -->|"结构一一对应"| B["ES 索引 Index<br/>如 product"]
    C["MySQL 一行<br/>一条商品记录"] -->|"每行 = 一份文档"| D["ES 文档 Document<br/>一份 JSON"]
    E["MySQL 字段<br/>name / price / tags"] -->|"mapping 里声明类型"| F["ES 字段 field<br/>text / scaled_float / keyword"]
```

**倒排索引**（**Inverted Index**）：正排是「文档 → 词」，倒排是「词 → 文档列表」——全文检索的本质是**从词反查文档**。

> **生活版——日历索引 vs 逐页翻书**：你手上一本电话簿，两种找人的方式：① **正排**=从第 1 页往后一页页扫，眼睛扫过每个名字找「张医生」（= 逐行全文扫，慢）；② **倒排**=书末先做好一张「关键词 → 页码」表（「张 → p8、p120」），查的时候直接翻开对应几页（= 顺着词查文档列表，快）。ES 用第 ② 种，MySQL 的 `LIKE '%xx%'` 只能是第 ① 种——这就是两者速度差的直观来源。
>
> **换成 ES**：写入文档时分析器先把文本切碎成**词条（Term）**，登记「每个词出现在哪些文档」；查询时按词直接反查这张「词 → 文档 ID」表，再求交 / 求并组合多词条件——复杂度从「扫全部行」（O(行数)）降到「按词查表求交」（O(词条数)）。

```mermaid
flowchart TD
    A["原文档<br/>如「Java 虚拟机 实战」"] --> B["分词器切词<br/>Java / 虚拟机 / 实战"]
    B --> C["词条表 Term Dictionary<br/>每个词登记出现在哪些文档<br/>如 java → 文档1、文档2"]
    C --> D["搜「Java 实战」<br/>按词反查两个文档列表"]
    D --> E["列表求交 / 求并<br/>得出命中文档并相关度打分排序"]
```

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

> ⏸️ **短期可以不学**：倒排索引的底层实现细节（Term Dictionary 的 FST 压缩前缀树、Posting List 的 Skip List 加速求交）属于引擎源码级推导，主线记住「词 → 文档列表」和三层结构名即可。**何时回来学**：ES 性能调优、面试深挖倒排索引实现时。**面试最低要求**：能画出「词 → 文档 ID 列表」的倒排结构，并解释为什么全文检索比 LIKE 快。
- **中文必须分词**（**Tokenization**，由分析器 Analyzer 完成）：不先分词，中文句子就是无法匹配的整串——**分词器（Analyzer）= 把一句话切成词的小工具**，英文按空格切就行，中文要靠词典 / 规则，所以要用专门的 IK 分析器。
  - `ik_max_word`：最细粒度，**建索引用**——尽量切出全部词元，倒排表覆盖面广。
  - `ik_smart`：粗粒度，**查询用**——按最合理的词切，query 更精准。
  - **铁律：索引与查询用同一套分词体系**——否则 query 词元与索引词元对不上，就会出现「明明有却搜不到」。

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

写入流程与「近实时」——理解了它，后面坑点里的「ES 当主存储用」才有依据：

> **生活版——草稿纸攒一会儿再誊写**：你写日记先写在草稿纸上，攒到固定间隔一次性誊进正式笔记本；正式本里的内容别人才能「翻阅查到」——誊写那一刻算起才可查，攒着的这 1 秒新内容查不到。
>
> **换成 ES**：文档写入先落**内存缓冲（buffer）**，默认每 **1 秒刷新（refresh）**把缓冲一次性转成**分段（Segment，倒排索引的存储单元，思路类似日志落盘的分区）**，转完这段才可被搜索到——这就是 ES「近实时（NRT）」：写入后最快 1 秒可搜，**不是「写入即实时」**。攒批 + 延迟可搜换来了高写入吞吐，代价是「刚写的搜不到、且无事务」。

```mermaid
sequenceDiagram
    participant Client as 客户端
    participant Buffer as 内存缓冲 buffer
    participant Disk as 磁盘分段 segment
    Client->>Buffer: ① 写入文档（先落内存缓冲，此刻不可搜）
    Note over Buffer: 攒批攒满默认 1 秒（refresh 间隔）
    Buffer->>Disk: ② 定期 refresh：缓冲一次转成 segment
    Note over Disk: ③ segment 变为可搜索的倒排片段
    Client->>Disk: ④ 搜索请求命中已就绪的分段
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

- **相关性打分（BM25，Best Matching 25）**：给命中文档排序的三要素——**词频饱和**（一个词出现很多次后不再无限加分，防关键词堆砌）+ **逆文档频率**（越稀有的词权重越高）+ **字段长度归一**（字段越短越相关）。
  - 默认理解即可；**搜索体验差先查分词与字段权重，不是调 BM25 参数**——按「分词对不对 → 字段权重 / bool 结构 → 最后才是打分公式」的顺序排查。
- **`match` vs `term` 的选型**——**用错是新手第一大坑**：
  - `match`：**分词后匹配**——查询词先被切开、各找各的倒排词项，用于 **text** 字段的模糊全文搜索（≈ `LIKE %xx%`，但更智能）。
  - `term`：**不分词**、按原词精确值匹配，用于 **keyword** 字段的精确过滤（≈ `WHERE status = 1`）。
  - 反直觉点：对 **text** 字段用 `term` 查，拿完整短语去匹配「分词后的词元」往往查不到——**倒排索引里存的是切碎的词，不是原值**。

### 3. 数据同步与深分页

```text
MySQL → ES 同步方案对比:
  双写(DB 写完再写 ES): 简单但强耦合 —— ES 抖动拖慢写链路, 且失败后补写麻烦
  MQ 异步(写 DB 发事件, 消费者写 ES): 解耦可重试 —— 秒级最终一致
  Canal 订阅 binlog: 与业务代码零侵入, 链路见阶段三全景图 —— 存量库无埋点的最佳解
  三者共性: 消费端要幂等(同文档重复写覆盖无害, 天然幂等) + 失败进重试/DLQ + 定期对账补偿
```

- **深分页（Deep Pagination）问题**——翻得越深越慢，且默认直接封死：
  - **为什么慢**：`from + size` 要先取回「前 from+size 条」再丢弃——第 1000 页（size=10，from=9990）意味着每个分片（**Shard，索引被拆成的小数据块**，摊到集群各节点上，思路类似把大表水平拆成多份）都要取满 `from+size = 10000` 条候选，from 越大浪费越多。
  - **默认封死**：ES 默认 `index.max_result_window=10000`，`from+size > 10000` 的请求直接拒绝。
  - 所以深翻页是**架构层面不可行**，不是调参能解的。
- 解法——**换成游标式翻页**：
  - `search_after`：游标式，拿上一页最后一条的排序值当起点继续翻，恒定成本（C 端列表「加载更多」就是这个思路）。
  - 全量导出用 **PIT（Point In Time，时间点快照）** + `search_after`；**scroll 已不推荐**（长期占资源不如游标稳定）：

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
    @Scheduled(cron = "0 30 2 * * ?")       // 每天 02:30 —— cron 表达式(6 段, 多一个秒)
    public void dailyReport() { /* 发日报 */ }
    // 注: cron 是 Quartz(Java 老牌定时任务库) 的风格 —— 6 段比前端熟的 crontab 5 段多一个「秒」位

    // 局限暴露点:
    // ① 多实例部署 → 三台机器各跑一遍(重复发日报): 要分布式锁挡
    // ② 上次跑挂/跑超时 → 无重试无告警无记录: 要自建账
    // ③ 任务互相抢线程池 → 一个慢任务饿死全部
}
```

- **XXL-Job 补齐**——把定时任务从「脚本」升级成「有运维面的服务」：
  - 管理台**可视化调度**：cron 在线改（不用改代码发版）。
  - **分片广播**：`shardIndex / shardTotal` 把大任务拆到多实例并行（如按用户 ID 取模分片跑对账）。
  - 失败重试 + 告警、手动触发与执行日志。

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

调度与执行分离的架构——看清这张图，就明白 XXL-Job 比 `@Scheduled` 多出来的「运维面」在哪：

> **生活版——总店派单给分店**：连锁餐饮不在某一家店里管全部门店的排班，而是中央调度台统一把「每天 02:30 出报表」的指令派下去；分店（执行器）收到就干、干完回执给总部。总部只管「什么时间派什么活」，分店只管「干活 + 上报结果」——**调度和执行彻底分开**。
>
> **换成 XXL-Job**：**调度中心（Admin，独立服务）**按 cron 到点发出触发信号；**执行器（Executor）**注册在各业务服务的 JVM 里，收到信号真正执行任务逻辑、把结果回执给调度中心——任务所在服务重启 / 扩容不影响「谁来调度」，调度中心也有补偿与告警兜底。

```mermaid
sequenceDiagram
    participant Admin as 调度中心 XXL-Job Admin
    participant Exec as 执行器 业务服务 JVM
    Admin->>Exec: ① 到点（cron 触发）发送任务信号
    Exec->>Exec: ② 本地执行任务逻辑（含分片广播）
    Exec->>Admin: ③ 完成后回执结果 / 失败上报
    Note over Admin: ④ 失败按策略重试 / 告警<br/>执行日志在线可查
```

- **时间轮**：Netty / Kafka 内部的定时结构——环形数组 + 指针按 tick 走动，任务挂格子里；海量短周期定时的 O(1) 实现（JDK `DelayQueue` 是优先队列 O(log n) 对照）。原理认知即可。

> ⏸️ **短期可以不学**：时间轮的实现细节属于中间件内部机制，主线知道「它是 Netty / Kafka 内部的高效定时结构」即可。**何时回来学**：面试被问 Netty / Kafka 定时机制，或需要自研定时调度时。**面试最低要求**：一句话说出时间轮是什么（环形数组 + 指针走动的 O(1) 定时结构）。

### 坑点提醒

- **mapping 里 text/keyword 用反**：聚合 text 字段直接报错或烧 fielddata（打开就是内存炸弹）——**需要排序 / 聚合的字段一律 keyword（或 multi-field 同时两种）**。
- **Canal 断点不续传**：binlog 位点丢失 = 全量重导或数据空洞——位点持久化 + 定期全量对账兜底。
- **`@Scheduled` 方法抛异常被吞**：任务静默停摆直到人肉发现——cron 任务第一行 try-catch 全包裹 + 上报；更优解直接上 XXL-Job。
- **ES 当主存储用**：近实时（refresh 1s）+ 无事务——**DB 永远是 OLTP 真相，ES 是只读视图**。
- **数据量小就别上 ES**：百万以下且无分词需求 → MySQL LIKE + 索引 / 全文索引够用；ES 换来搜索能力的同时引入了集群运维成本（分片、副本、调优）——别为了「显得专业」上 ES。
- **快照备份要有**：snapshot API 定期把索引备到远端仓库——ES 误删 / 索引损坏没有「恢复出厂」，没快照就是真没了。
- **集群角色分工**：master 管元数据、data 存数据、coordinating 只做协调（接查询、归并结果）——大集群三角色分离部署。

分片与副本——ES 集群里最能解释「为什么要运维」的两个概念：

> **生活版——一本拆册 + 每册备份**：1 亿条的电话簿一本太厚，拆成几本小册子分柜存放（= **分片 Shard**：索引被横向切开摊到多台机器）；每本小册子再抄一份放别的柜子（= **副本 Replica**：一份数据的冗余拷贝）——某柜烧了，备查的抄本顶上；两柜还能同时接待查询分摊压力。
>
> **换成 ES**：**分片（Shard）**把索引横向切多块、分布到集群各节点，单节点装不下的数据才放得下；**副本（Replica）**是分片的一份冗余拷贝——主分片挂了副本顶上（高可用），读请求也能打到副本（负载分担）。分片数决定写入并行度、副本数决定读吞吐与容错，**上线前就要定好**。

```mermaid
flowchart TD
    A["索引 Index<br/>全量数据"] --> B["分片 0<br/>第 1 份数据块 · 存节点 1"]
    A --> C["分片 1<br/>第 2 份数据块 · 存节点 2"]
    B --> D["副本<br/>分片 0 的备份 · 存节点 2"]
    C --> E["副本<br/>分片 1 的备份 · 存节点 1"]
    F["读请求按副本轮询"] --> D
    F --> E
```

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

## 常见面试题

### Q1：Elasticsearch 的倒排索引是什么？为什么比 MySQL LIKE 快？

**答**：

**标准结论**：倒排索引（Inverted Index）是「词 → 文档列表」的反向映射——建索引时先分词，再为每个词记录它出现在哪些文档里；查询时直接按词反查文档列表，不用全表扫描。

**底层原理**：
- MySQL 的 `LIKE '%xx%'` 无法用 B+ 树的前缀匹配优化，只能全表扫、逐行做字符串匹配。
- ES 在写入时就把文本切成词元、建好「词 → 文档 ID」的倒排表，查询复杂度从 O(行数) 降到 O(词条数)，还能用求交 / 求并组合多词条件。
- 这也是为什么 ES 叫全文检索引擎——**数据结构就是为搜索设计的**。

**工程实践**：
- 前提是**分词正确**（中文用 IK，索引与查询分词器一致）。
- 只对需要搜索的字段建 text 类型，**精确过滤用 keyword**。

**常见误区**：
- 数据量小、无分词需求时上 ES 属于过度设计——MySQL LIKE 加索引够用，别为「显得专业」引入集群运维成本。

### Q2：match 和 term 的区别？text 和 keyword 怎么选？

**答**：

**标准结论**：`match` 是**分词匹配**（用于 text 字段），`term` 是**不分词精确值匹配**（用于 keyword 字段）；需要排序 / 聚合 / 精确过滤的字段用 keyword，需要全文检索的用 text。

**底层原理**：
- ES 对 text 字段建索引时先分词，**倒排索引里存的是「词元」而不是完整原值**。
- 对 text 字段做 `term` 查询，拿完整短语去匹配词元，往往查不到。
- keyword 不分词、原值整体建索引，`term` 才能精确命中。

**工程实践**：
- `tags`、`status`、订单号等用 keyword；标题、描述用 text。
- 一个字段两种需求可以配 **multi-field**（text + keyword 并存）。

**常见误区**：
- 对 text 字段做聚合会报错或烧 fielddata（内存炸弹）——**要排序 / 聚合的字段一律 keyword**。
- mapping 上线后改类型要重建索引，**设计期就要定对**。

### Q3：ES 深分页为什么慢？怎么解决？

**答**：

**标准结论**：`from + size` 翻页是「取回前 from+size 条再丢弃」，且默认 `index.max_result_window=10000` 直接封死深翻页；解法是 `search_after` 游标式翻页，全量导出用 PIT + search_after（scroll 已不推荐）。

**底层原理**：
- ES 是分布式的，一次查询要广播到全部分片——每个分片都要取满「from + size」条候选，归并后再丢弃前 from 条。
- from 越大，每个分片取回的候选越多，**网络与内存开销线性增长**——所以深翻页在架构上就不可行，不是调参能解决的。

**工程实践**：
- C 端列表用「加载更多」+ `search_after`（传上一页最后一条的排序值）。
- 后台全量导出用 **PIT 时间点快照** 保证游标稳定。

**常见误区**：
- 把 `max_result_window` 调大或加大 size 只是治标——分片归并的根本问题还在。
- scroll 快照会长期占用资源，**新代码一律用 search_after**。

### Q4：MySQL 的数据怎么同步到 Elasticsearch？怎么保证一致性？

**答**：

**标准结论**：三种方案——**双写**（写 DB 后再写 ES，简单但强耦合）、**MQ 异步**（写 DB 发事件，消费者写 ES，解耦可重试）、**Canal 订阅 binlog**（伪装成从库拉 binlog，业务零侵入，推荐）。共性：消费端幂等 + 失败进重试 / DLQ + 定期对账补偿。

**底层原理**：
- ES 是检索加速层不是 OLTP 真相，**同步可靠性要靠消息链路保证**。
- Canal 订阅的是 binlog 物理变更日志，任何 DB 改动都能捕获，存量库无需埋点。
- 写 ES 天然幂等——同一文档重复写覆盖无害，所以重试安全。

**工程实践**：
- Canal 断点要持久化（binlog 位点丢失 = 数据空洞），**定期全量对账兜底**。
- 「对账 + 补偿」是同步链路的**最后防线**。

**常见误区**：
- 以为双写最简单就双写——ES 抖动会拖慢写链路，失败补写麻烦。
- 别让 ES 当唯一存储，它近实时（refresh 1s）+ 无事务，**业务真相永远在 DB**。

### Q5：定时任务为什么用 XXL-Job 而不用 Spring @Scheduled？

**答**：

**标准结论**：`@Scheduled` 只适合单实例、轻量任务；多实例部署会重复执行、跑挂无重试无告警、慢任务会饿死其他任务。XXL-Job 补齐了可视化调度、分片广播、失败重试、告警与执行日志。

**底层原理**：
- `@Scheduled` 的三个局限——多实例各跑一遍（重复发日报）、失败 / 超时无感知、任务共用线程池互相拖累。
- XXL-Job 用「**调度中心 + 执行器**」架构，调度与执行分离，支持 cron 在线修改、手动触发、分片广播（`shardIndex / shardTotal` 按取模把大任务拆到多实例并行）。

**工程实践**：
- 分布式下要么用**分布式锁**挡重复执行，要么直接上 XXL-Job 分片广播。
- `@Scheduled` 方法异常默认被吞，**第一行就要 try-catch 全包裹 + 上报**。

**常见误区**：
- 以为加个分布式锁就能用 `@Scheduled` 硬扛——日志、重试、告警都要自建，成本比接入 XXL-Job 高得多。
- 别把「cron 表达式」当难点，XXL-Job 管理台在线改 cron 才是生产常态。
