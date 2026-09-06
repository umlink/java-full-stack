# MongoDB：从基础到资深进阶

> 所属：阶段八 数据库专家
> 定位：MongoDB 是**文档型 NoSQL**——以 JSON 风格的文档存数据，schema 灵活、水平扩展容易。适合「字段常变、海量写入、快速迭代」场景。**记住：它牺牲了强事务和复杂关联，换来灵活 schema 和水平扩展——选它前先确认你真的不需要复杂关联事务。**

## 快速入门（能跑）

### 核心关键词速查
| 关键词 | 一句话大白话 | 例子 |
|-|-|-|
| 数据库 | 命名空间 | `shop` |
| 集合（Collection） | ≈ 关系表 | `users` |
| 文档（Document） | ≈ 一行，JSON 格式 | `{name:"张三", age:20}` |
| ObjectId | 自动生成的主键 | `_id` |
| schema 灵活 | 每文档字段可不同 | 一条有 address、一条没有 |
| 索引 | 加速查询 | `{user_id:1}` |
| 副本集 | 高可用 | 主从自动切换 |
| 分片集群 | 水平扩展 | 海量数据 |

### 最简可运行示例
```javascript
// 用 MongoDB Java Driver 读写(带详细注释)
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

@Service
public class UserStore {
    private final MongoCollection<Document> collection;  // 用户集合

    public UserStore(MongoClient mongoClient) {
        this.collection = mongoClient.getDatabase("shop").getCollection("users");
    }

    // 插入: 文档就是 JSON, 字段随意
    public void insert(String name, Integer age) {
        Document doc = new Document("name", name)     // 键值对
                       .append("age", age)             // 可加任意字段
                       .append("created_at", new java.util.Date());
        collection.insertOne(doc);                     // 插入一条
    }

    // 查询: 用 Document 表达条件
    public Document findByName(String name) {
        return collection.find(new Document("name", name)).first();  // 找第一条
    }
}
```

> **代码备注（逐行解释）**：
> - `MongoClient`：MongoDB 客户端（自动注入），连接即可用。
> - `getDatabase("shop").getCollection("users")`：定位到「数据库.集合」（≈ 库.表）。
> - `new Document("name", name).append("age", age)`：**文档即 JSON**——字段灵活，不用预先定义 schema。
> - `insertOne(doc)`：插入一条；`find(...).first()`：查第一条；`find(...)` 返回游标可遍历。
> - **`_id` 自动生成**：若不指定，Mongo 自动生成 ObjectId——就是主键。
> - 对比 MySQL：**无需先建表**，插入第一条文档就自动建了集合——这是 schema-less 的魅力。

## 核心概念

### 1. 文档模型 vs 关系模型
| 维度 | MongoDB | MySQL |
|-|-|-|
| 结构 | JSON 文档，字段灵活 | 表，强 schema |
| 关联 | 文档内嵌或引用 | JOIN |
| 事务 | 文档级原子（事务有限） | ACID 全支持 |
| 扩展 | 分片，水平扩展容易 | 主从/分库分表 |
> **该怎么做**：字段常变、一对一内嵌、海量写入用 MongoDB。
> **不该怎么做**：复杂多表关联、强事务（如支付）用 MongoDB——那是 MySQL 的地盘。

### 2. 插入与查询操作符
```javascript
// 插入多条
collection.insertMany(List.of(doc1, doc2));

// 查询操作符: $gt/$in/$regex 等
collection.find(new Document("age", new Document("$gt", 18)))     // age > 18
           .sort(new Document("created_at", -1))                   // 按时间倒序
           .skip(0).limit(10);                                     // 分页

// 更新: 匹配条件 + 更新操作
collection.updateOne(
    new Document("name", "张三"),                       // 条件
    new Document("$set", new Document("age", 30)));     // $set: 只改 age 字段
```

## 进阶

### 1. 索引与查询优化
```javascript
// 建索引: 加速按 user_id 查询
collection.createIndex(new Document("user_id", 1));
// 组合索引 + 排序字段
collection.createIndex(new Document("user_id", 1).append("created_at", -1));
```
> **该怎么做**：高频查询建索引；排序字段放索引里。
> **不该怎么做**：全集合扫（无索引）——数据一多就慢；也别建太多索引（写开销）。

### 2. 聚合管道（Aggregation Pipeline）——MongoDB 的「SQL 增强」
```javascript
// 聚合管道: 一组阶段($match/$group/$project)串成数据处理流水线
import static com.mongodb.client.model.Aggregates.*;   // match/group/sort 等阶段
import static com.mongodb.client.model.Accumulators.*; // avg/sum/push 等累加器(在 Accumulators 类!)
import static com.mongodb.client.model.Sorts.*;        // descending/ascending(在 Sorts 类!)
import static com.mongodb.client.model.Filters.*;

// 需求: 按 status 分组统计 + 求平均金额 + 排序(≈ SQL 的 GROUP BY + AVG + ORDER BY)
var result = collection.aggregate(List.of(
    match(eq("status", 1)),                              // $match: 过滤(≈ WHERE)
    group("$userId",                                    // $group: 分组(≈ GROUP BY)
        avg("avgAmount", "$amount"),                    //   avgAmount = 每组 avg(amount)
        sum("total", "$amount")),                       //   total = 每组 sum(amount)
    sort(descending("avgAmount"))                       // $sort: 排序(≈ ORDER BY)
));
result.forEach(doc -> System.out.println(doc));          // 迭代结果
```
> **代码备注（逐行解释）**：
> - `match(...)`：**$match** 阶段——筛选（≈ `WHERE`），放最前能减少后续处理量。
> - `group("$userId", ...)`：**$group** 阶段——按字段分组（≈ `GROUP BY`），`avg`/`sum` 是累加器。
> - `sort(...)`：**$sort** 阶段——排序（≈ `ORDER BY`）。
> - 聚合管道把「过滤/分组/排序/筛选」串成**一个查询**，在数据库内完成，不拉全量到内存——这是 MongoDB 做统计的核心。
> - 对比 MySQL：相当于一条 `GROUP BY` + 聚合函数；但 MongoDB 的管道更灵活（`$lookup` 关联、`$unwind` 拆数组）。

### 3. 副本集（高可用，生产标配）
```javascript
// 副本集 rs0: 1主2从, 主挂了自动选新主 —— 生产必须
// 连接串: mongodb://host1,host2,host3/?replicaSet=rs0
// 读偏好(readPreference): 让读走从库分担压力
// mongodb://hosts/?replicaSet=rs0&readPreference=secondaryPreferred
```
> **该怎么做**：生产用副本集（≥3 节点，1 主 2 从），主故障自动切换；`readPreference=secondaryPreferred` 让读走从库。
> **不该怎么做**：单节点裸奔——宕机即不可用。

### 4. 分片集群（水平扩展）
- **分片键（Shard Key）**：把数据散到多个分片的字段（如 `user_id`），**选高频查询 + 高基数 + 均匀分布**的字段。
- **分片 + 副本集**：每个分片是完整副本集，共同承担海量数据。
```javascript
// 分片方式: ① hashed(哈希) 分片 → 打散均匀, 避免写热点 —— 适合高并发写入
//          ② ranged(范围) 分片 → 利于范围查询, 但单调递增键(时间戳)会产生写热点
// 分片键选择: user_id(高基数均匀) → hashed 好; 时间戳 → ranged 会写热点, 要避免
// 5.0 起支持在线 resharding, 可平滑调整分片键
```
> **该怎么做**：单机扛不住时按高频**高基数且均匀**的字段（user_id）做分片键；高并发写入选 **hashed** 分片避免写热点。
> **不该怎么做**：用单调递增的**时间戳**做范围分片键——写全打到最新分片，该分片成瓶颈。

### 5. 事务（有限）
```javascript
// 副本集/分片集群下支持多文档事务(4.0+)
try (var session = mongoClient.startSession()) {   // 用注入的 mongoClient 开会话
    session.startTransaction();
    collection1.insertOne(doc1, session);        // 第1个操作: 传 session 进事务
    collection2.updateOne(filter, update, session);  // 第2个操作: 必须也传 session, 否则不进事务!
    session.commitTransaction();                 // 全部成功才提交, 任一失败回滚
}
```
> **该怎么做**：确实需要强一致 + 多文档操作时，用事务（4.0+，需副本集/分片）。
> **不该怎么做**：能不用就不用——事务会降低性能；**且事务内每个操作都必须传 `session`**，漏传的操作不在事务里。

## 进阶补充：内嵌 vs 引用建模（文档模型的核心决策）

> MongoDB 建文档时最核心的决策：**数据是内嵌（embedded）还是引用（reference）**，取决于「访问模式 + 数据规模」。

| 决策 | 该怎么做 | 原由 |
|-|-|-|
| **内嵌（Embed）** | 一对一 / 一对少（如订单内含几个地址、文章含标签数组） | 一次查询拿全，避免跨文档查询 |
| **引用（Reference）** | 一对多 / 多对多（如用户的多条订单、关注关系） | 避免重复、数据独立更新、控制文档大小（16MB 上限） |

```javascript
// 内嵌: 订单里直接嵌收货地址(一对少)
Document order = new Document("orderNo", "A001")
    .append("items", List.of(/* ... */))
    .append("addr", new Document("city", "上海").append("street", "xx路"));

// 引用: 用户的多条订单(一对多) —— 订单单独存, 用户只存订单 id 引用
Document user = new Document("name", "张三").append("orderIds", List.of("O1", "O2"));
```
> **该怎么做**：内嵌用于「随主文档一起读、变化少」的数据；引用用于「独立增长、需单独更新」的数据。
> **不该怎么做**：把用户的所有订单都内嵌进用户文档——随订单增长会顶到 16MB 文档上限，且更新整个文档代价大。

## 场景与红线（怎么做 / 不该怎么做）

| 场景 | ✅ 该怎么做 | ❌ 不该怎么做 |
|-|-|-|
| 埋点/事件/日志 | MongoDB（字段灵活，海量写入） | 每次 ALTER 加列的 MySQL |
| 动态字段/灵活 JSON 文档 | MongoDB 文档 | 强 schema 数据库（字段常变改起来痛苦） |
| 用户画像（字段多变） | MongoDB | 频繁 ALTER |
| 复杂关联报表 | MySQL/PG | MongoDB（关联弱） |
| 强事务核心业务（支付） | MySQL/PG | MongoDB（多文档事务 4.0+ 支持，但不如 MySQL 久经考验） |
| 海量写入 + 水平扩展 | MongoDB 分片 | 单机 MySQL 硬扛 |

## 红线小结（必背）

1. **schema 灵活是双刃剑**：字段常变就用它，需要强约束强事务就别用。
2. **严格用索引**：否则全集合扫描。
3. **生产必须副本集**：单节点不能当线上；读偏好 secondaryPreferred 分担读。
4. **聚合统计用管道**：`$match/$group/$sort` 在库内完成，别拉全量到内存算。
5. **关联/事务慎重**：Mongo 不强在这——优先 MySQL/PG。
6. **什么时候用 MongoDB**：字段常变 + 海量写入 + 快速迭代——三个特征同时满足才值得。
7. **别为「NoSQL 先进」而上**：用关系型能解决的就别引入 MongoDB——多一种存储多一份运维成本。

## 进阶自测

- [ ] 能说清文档模型 vs 关系模型的差异（schema 灵活 / 关联 / 事务）
- [ ] 能用 Document 写插入/条件查询/`$set` 更新
- [ ] 能用聚合管道（$match/$group/$sort）做分组统计，并解释各阶段
- [ ] 能说清 ObjectId 是什么、为什么自动生成
- [ ] 能设计副本集（1主2从 + 读偏好）与分片键选择
- [ ] 能判断「什么时候该用 MongoDB、什么时候该用 MySQL」（核心判断力）
