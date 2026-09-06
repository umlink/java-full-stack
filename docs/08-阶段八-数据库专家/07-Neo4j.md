# Neo4j：从基础到资深进阶

> 所属：阶段八 数据库专家
> 定位：Neo4j 是**图数据库**——以「节点 + 关系 + 属性」建模，擅长**多跳关系查询**。适合「社交关系、推荐、反欺诈、知识图谱」场景。**记住：它是「关系」的专用引擎——只有当你需要多跳遍历（如「朋友的朋友的朋友」）时才值得用，简单一对多还是用关系型。**

## 快速入门（能跑）

### 核心关键词速查
| 关键词 | 一句话大白话 | 例子 |
|-|-|-|
| 节点（Node） | 一个实体 | 一个用户/一个商品 |
| 关系（Relationship） | 两个节点的连接 | FRIENDS_WITH |
| 属性（Property） | 节点/关系的字段 | name/age |
| 标签（Label） | 节点分类 | `:Person` |
| Cypher | 图查询语言 | `MATCH (n) RETURN n` |
| 路径（Path） | 节点间的连缀 | 多跳关系 |

### 最简可运行示例
```cypher
// 创建节点 + 关系(带详细注释)
CREATE (a:Person {name: '张三', age: 25})     // 创建 Person 节点, 带属性
CREATE (b:Person {name: '李四', age: 24})
CREATE (a)-[:FRIENDS_WITH]->(b)               // 张三 认识 李四(关系)

// 查询: 找"认识李四"的人
MATCH (p:Person {name: '李四'})<-[:FRIENDS_WITH]-(p2)   // 顺着关系反查
RETURN p2.name;
```

> **代码备注（逐行解释）**：
> - `CREATE (a:Person {...})`：创建节点——`a` 是变量名，`:Person` 是标签（类别），`{...}` 是属性。
> - `CREATE (a)-[:FRIENDS_WITH]->(b)`：建关系——箭头方向表示「a → b」是 `FRIENDS_WITH`。
> - `MATCH (p:Person {name:'李四'})`：先匹配到「李四」这个节点。
> - `<-[:FRIENDS_WITH]-(p2)`：顺着「认识」关系反查——找到"指向李四"的节点 `p2`（即认识他/她的人）。箭头方向决定了遍历方向。
> - `RETURN p2.name`：返回结果列。
> - 对比 SQL：这在 SQL 里要自关联表多表 JOIN + 递归——图数据库用 `MATCH` 一行表达多跳关系。

### Java 集成（Spring Data Neo4j）
```java
// Spring Data Neo4j: 用注解把实体映射成节点, 像操作普通对象一样查图
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.repository.Neo4jRepository;

@Node("Person")                            // 映射到 :Person 节点
public class Person {
    @Id private Long id;                    // @Id: 节点主键
    private String name;
    // private String phone;  ... getter/setter 省略, 属性即节点属性字段
}

public interface PersonRepository extends Neo4jRepository<Person, Long> {
    // 方法名即查询: 找 name=xx 的 Person(Spring Data 按命名规则自动生成 Cypher)
    Person findByName(String name);
}
```
> **代码备注（逐行解释）**：
> - `@Node("Person")`：把 Java 类映射成 Cypher 的 `:Person` 节点。
> - `@Id`：节点主键；其余字段自动成为节点属性。
> - `Neo4jRepository<Person, Long>`：Spring Data 的 Neo4j 仓库，`findByName(name)` 方法名即查询（自动生成 `MATCH (p:Person) WHERE p.name=$name RETURN p`）。
> - **为什么这样好**：Java 后端用 Spring Data Neo4j，可像操作 JPA 一样操作图，无需手写 Cypher——对 Java 背景友好（与 Redis 的 Spring Data、Mongo 的 Driver 体例一致）。

## 核心概念

### 1. 图模型 vs 关系模型
| 维度 | Neo4j | MySQL |
|-|-|-|
| 建模 | 节点/关系/属性 | 表外键 |
| 多跳关系 | `MATCH (a)-[*2..3]->(b)` 高效 | 递归 JOIN 又慢又难写 |
| 关系查询 | 顺着边遍历，天然快 | 深度关联 JOIN 爆炸 |
| 强约束 | 弱 | 强 schema/事务 |
> **该怎么做**：多跳关系（社交、推荐、路径）用图数据库。
> **不该怎么做**：简单一对多（如「一个用户多条订单」）用图——那是表/聚合的活，用关系型更合适。

### 2. 节点与关系模型
```cypher
// 更完整的建模: 用户关注话题, 话题含文章
CREATE (u:User {id: 1, name: '张三'})
CREATE (t:Topic {name: 'Java'})
CREATE (a:Article {title: 'Spring 入门'})
CREATE (u)-[:FOLLOWS]->(t)          // 张三 关注 Java 话题
CREATE (t)-[:HAS]->(a)               // Java 话题 包含文章
```
> **该怎么做**：实体建节点（含业务实体），语义连接建关系，都可加属性。
> **不该怎么做**：把关系当节点/属性硬塞——图模型就失去了价值（关系应该是边）。

## 进阶

### 3. 多跳查询（图数据库的灵魂）
```cypher
// 找"朋友的朋友"（2 跳）
MATCH (me:Person {id: 1})-[:FRIENDS_WITH*2..2]-(friend)
RETURN friend.name;

// 找"推荐路径"（1-3 跳）
MATCH (me:Person {id: 1})-[:FRIENDS_WITH*1..3]-(candidate)-[:LIKES]->(product)
WHERE NOT EXISTS((me)-[:LIKES]->(product))
RETURN product, count(*) as score ORDER BY score DESC;
```
> **该怎么做**：多跳（`*2..3`）、路径、推荐用 `MATCH` 加关系模式一行表达——这是图数据库的核心价值。
> **不该怎么做**：用递归 JOIN 硬写多跳——图数据库就是为这个优化的。

### 4. 索引与约束（Neo4j 5.x 语法）
```cypher
// 给 name 建唯一约束(类似主键) —— 注意 5.x 语法: FOR ... REQUIRE (旧 ASSERT 语法已移除)
CREATE CONSTRAINT person_name_unique IF NOT EXISTS FOR (p:Person) REQUIRE p.name IS UNIQUE;

// 建普通索引加速查询 —— 5.x 起统一使用 FOR (n:Person) ON (n.name)(旧 ON :Person(name) 已移除)
CREATE INDEX person_name_index IF NOT EXISTS FOR (n:Person) ON (n.name);

// fulltext 全文索引(图搜索)
CREATE FULLTEXT INDEX person_names IF NOT EXISTS FOR (n:Person) ON EACH [n.name];
```
> **该怎么做**：高频查询/唯一字段建约束或索引。
> **不该怎么做**：无索引全图扫描——数据多了就慢。

### 5. 图算法（GDS 库）
> **注意**：运行 GDS 算法**必须先创建图投影**（`gds.graph.project`），否则 `'graph'` 不存在会报错。

```cypher
// ① 先做图投影: 把 Person 节点 + FRIENDS_WITH 关系投影成一个命名图 'graph'
CALL gds.graph.project('graph', 'Person', 'FRIENDS_WITH');

// ② PageRank 找重要节点(社区发现/推荐)
CALL gds.pageRank.stream('graph') YIELD nodeId, score
RETURN nodeId, score ORDER BY score DESC;

// ③ 最短路径: 找两人之间的最短关系链 (必须带算法名 dijkstra; sourceNode/targetNode 传节点 id)
MATCH (a:Person {name:'张三'}), (b:Person {name:'李四'})
CALL gds.shortestPath.dijkstra.stream('graph',
    { sourceNode: id(a), targetNode: id(b), relationshipTypes: ['FRIENDS_WITH'] })
YIELD path RETURN path;

// ④ 连通分量: 找独立的社群/团伙
CALL gds.louvain.stream('graph') YIELD nodeId, communityId RETURN nodeId, communityId;
```
> **该怎么做**：社区发现（Louvain）、重要度（PageRank）、最短路径用 GDS 算法库——这些是图数据库独有的能力。
> **不该怎么做**：不先 `gds.graph.project` 就直接跑算法（图不存在会报错）；`sourceNode/targetNode` 误传 Cypher 节点变量（应传 `id(...)`）。

### 6. 高可用：因果集群（Core / Read Replica）
```cypher
// Neo4j 因果集群: 核心节点(写+容错) + 只读副本(读扩展)
// cluster mode: core + read-replica —— 核心挂了自动选新核心, 只读副本可横向扩展读
// 配置: 多个 core(≥3, Raft 多数派) 负责写; read-replica 只读, 扛读流量
// 注: 5.x 后术语倾向用 primary/secondary, "causal cluster" 概念被新架构演进(以官方文档为准)
```
> **该怎么做**：生产用**因果集群**（≥3 个 core 节点保证高可用 + read-replica 横向扩展读）；写走 core、读走 read-replica。
> **不该怎么做**：单节点裸奔——宕机即不可用。核心节点用**奇数个**（3 或 5），偶数个容错能力不变反而浪费。

## 场景与红线（怎么做 / 不该怎么做）

| 场景 | ✅ 该怎么做 | ❌ 不该怎么做 |
|-|-|-|
| 社交关系/好友推荐 | Neo4j（多跳遍历） | 自关联表 + 递归 JOIN |
| 反欺诈（资金链路） | Neo4j 查环/路径 | 关系型难查深链 |
| 知识图谱 | Neo4j 节点边 | 表 + 连接表 |
| 权限/依赖关系 | Neo4j 路径推导 | 递归 |
| 简单一对多/聚合 | MySQL/PG | Neo4j（过度） |
| 纯聚合统计 | PG/ES | Neo4j |

## 红线小结（必背）

1. **图数据库是「关系」的专用引擎**：多跳遍历是它的主场。
2. **什么时候用 Neo4j**：明确需要「多跳关系、路径、图算法」——社交/推荐/反欺诈/知识图谱。
3. **别过度用**：简单一对多、纯聚合统计用关系型/PG/ES 更合适（图数据库维护成本高）。
4. **建模用节点 + 关系**：关系是边，不是节点/属性。
5. **生产用因果集群**：≥3 核心节点多数派写 + read-replica 读扩展——单节点别当线上。
6. **核心判断**：先问「我的查询是多跳关系吗？」是→Neo4j；不是→关系型。

## 进阶自测

- [ ] 能说清图模型 vs 关系模型的差异（节点/关系/多跳）
- [ ] 能用 Cypher 创建节点 + 关系 + 属性
- [ ] 能写多跳查询（`*2..3`）表达「朋友的朋友」
- [ ] 能用 GDS 算法（PageRank/最短路径/Louvain）做分析
- [ ] 能说清因果集群（core + read-replica）如何高可用与读扩展
- [ ] 能说出图数据库的核心价值（多跳遍历）与适用场景（社交/推荐/反欺诈）
