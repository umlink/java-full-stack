# Redis：从基础到资深进阶

> 所属：阶段八 数据库专家
> 定位：Redis 是「内存级读写 + 丰富数据结构 + 原子操作」的缓存/存储引擎。本教程从「能 set/get」到「数据结构选型、缓存一致性、分布式锁、持久化」，再到「Lua 脚本/管道/事务」等**企业级惯用法**，最后到「主从 + 哨兵 / Cluster」的**高可用与扩展**。**记住一条红线：Redis 是缓存/加速器，不是核心业务数据的唯一事实源。**

## 快速入门（能跑）

### 核心关键词速查
| 关键词 | 一句话大白话 | 例子 |
|-|-|-|
| key-value | 键值对存储 | `SET user:1 "张三"` |
| 过期时间（TTL） | 数据到时自动消失 | `SET k v EX 60` |
| String（字符串） | 最基础：计数/缓存单值 | `INCR page_view` |
| Hash（哈希） | 存对象（字段:值） | `HSET user:1 name 张三` |
| List（列表） | 有序可重复 | `LPUSH queue msg` |
| Set（集合） | 不重复 | `SADD follow:1 2` |
| ZSet（有序集合） | 带分数的排序集合 | `ZADD rank 100 a` |
| 持久化 | 数据落盘防丢 | RDB / AOF |

### 最简可运行示例
```java
// 用 Spring Data Redis 读写(带详细注释)
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;

@Service
public class CacheService {
    private final StringRedisTemplate redis;   // Redis 操作模板(已自动配置)

    public CacheService(StringRedisTemplate redis) { this.redis = redis; }

    // 写一个值, 60 秒过期
    public void set(String key, String val) {
        redis.opsForValue().set(key, val, Duration.ofSeconds(60));  // set + TTL
    }
    // 读一个值(不存在返回 null)
    public String get(String key) {
        return redis.opsForValue().get(key);   // 读不到返回 null, 不是抛异常
    }
}
```

> **代码备注（逐行解释）**：
> - `StringRedisTemplate`：Spring 提供的 Redis 模板，`key`/`value` 都是 String（最常用），已由 Boot 自动装配。
> - `opsForValue()`：操作 String 结构；相应地有 `opsForHash()`/`opsForList()`/`opsForZSet()` 等。
> - `set(key, val, Duration.ofSeconds(60))`：写入并设置 60 秒 TTL——**给缓存设 TTL 是铁律**（防止脏数据永远存在）。
> - `get(key)`：读不到返回 `null`（不是抛异常），调用方要判空。所以缓存读要配合「空值处理」。

## 核心概念

### 1. 数据结构与典型场景
| 结构 | 底层 | 典型场景 | 该怎么做 |
|-|-|-|-|
| String | 简单动态字符串 | 计数器、缓存单值、分布式锁 | 计数用 `INCR`（原子） |
| Hash | 哈希表 | 存对象（购物车 `{skuId:cnt}`） | 一个 key 多个字段 |
| List | 双向链表 | 简单队列/栈、消息 | 左右两端 `LPUSH/RPOP` |
| Set | 哈希/整数集合 | 去重、共同关注（交集） | `SINTER` 求交集 |
| ZSet | 跳表+哈希 | 排行榜、延迟队列 | score 决定排序 |

### 2. 过期与淘汰策略
```java
// 给 key 设置过期(读的时候同步刷新, 顺带延长)
import java.util.concurrent.ThreadLocalRandom;
redis.opsForValue().set(key, val,
        Duration.ofMinutes(30).plusSeconds(ThreadLocalRandom.current().nextInt(60)));  // 随机 +0~59s 防雪崩
```
> **该怎么做**：所有缓存都设 TTL，且**加一点随机扰动**（`ThreadLocalRandom`）——否则大量 key 同时过期会造成「缓存雪崩」。
> **不该怎么做**：设永久 TTL（数据永不更新，脏数据风险）。**maxmemory-policy 要选**：默认 `noeviction`（内存满写报错），常用 `allkeys-lru`。

## 进阶：企业级惯用法与专业实践

### 1. 缓存三大经典问题（穿透 / 击穿 / 雪崩）

```java
// 防穿透: 查询不存在的 key, 缓存 null 占位(短 TTL), 避免每次都打 DB
// 注意: StringRedisTemplate 的 value 是 String, 因此这里存的是 JSON 字符串, 不直接存 User 对象
public User getUser(Long id) {
    String key = "user:" + id;
    String json = redis.opsForValue().get(key);
    if (json != null) return JsonUtil.fromJson(json, User.class);        // 命中: 反序列化
    User u = userRepo.findById(id);
    if (u == null) {
        redis.opsForValue().set(key, NULL_PLACEHOLDER, Duration.ofSeconds(60));  // 查不到也缓存空标记
    } else {
        redis.opsForValue().set(key, JsonUtil.toJson(u), Duration.ofSeconds(60));
    }
    return u;
}
```

| 问题 | 现象 | 该怎么做 |
|-|-|-|
| 穿透 | 查不存在的 key 一直打 DB | 缓存 null 值 / 布隆过滤器 |
| 击穿 | 某热点 key 过期瞬间大量请求打 DB | 互斥锁重建 / 逻辑过期 |
| 雪崩 | 大量 key 同时过期 | 随机 TTL / 集群多级缓存 |

> **不该怎么做**：缓存穿透用「查询前不加任何判断」——恶意请求会打垮 DB。

### 2. 缓存一致性（先更 DB 再删缓存）
```java
@Transactional
public void update(User u) {
    userRepo.update(u);               // ① 先更数据库
    redis.delete("user:" + u.getId()); // ② 再删缓存(下个请求重新回填, 读到新值)
}
```
> **该怎么做**：**先更新数据库、再删缓存**（Cache-Aside）——保证下个请求读到的是最新值。
> **不该怎么做**：先删缓存再更 DB——并发下会把旧数据放回缓存（数据不一致）。

### 3. Redis 事务 / Lua 脚本（原子性）
```java
// Lua 脚本: 把多个命令做成一个原子操作(如扣库存: 判断+扣减)
String lua = "local c = redis.call('get', KEYS[1]) "
           + "if c and tonumber(c) >= tonumber(ARGV[1]) then "
           + "  redis.call('decrby', KEYS[1], ARGV[1]) return 1 "
           + "else return 0 end";
// 注意: 返回类型要与脚本 RETURN 一致; 用 RedisTemplate<String,Object> 或配好序列化器,
//       否则 StringRedisTemplate 的字符串序列化器对 Long 结果反序列化会出问题
Long r = redis.execute(new DefaultRedisScript<>(lua, Long.class),
        List.of("stock:" + skuId), String.valueOf(count));   // 原子: 判断+扣减一个脚本完成
```
> **该怎么做**：需要「判断 + 操作」的原子性（扣库存、限流）用 **Lua**——Redis 原子执行整个脚本，无需担心并发。
> **不该怎么做**：`GET` → 判断 → `DECR` 三步——并发下会超卖。Redis 事务（MULTI/EXEC）只是「批量执行不被打断」，不支持回滚，慎用。

### 4. 管道（Pipeline）批量操作
```java
// 管道: 一次网络往返批量执行多条命令(省网络开销)
import java.nio.charset.StandardCharsets;
byte[] valueBytes = val.getBytes(StandardCharsets.UTF_8);   // String 要转成 byte[] 才能传
redis.executePipelined((RedisCallback<Object>) conn -> {
    for (Long id : ids) {
        conn.stringCommands().set(("user:" + id).getBytes(StandardCharsets.UTF_8), valueBytes);
    }
    return null;
});
```
> **该怎么做**：批量 set/get 用管道——把多个命令合并成一次网络往返，吞吐大幅提升（异步批量落库、批量预热缓存）。

### 5. 分布式锁（Redisson）
```java
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

public void deductStock(Long skuId) throws InterruptedException {
    RLock lock = redisson.getLock("stock:lock:" + skuId);   // 加锁的 key
    boolean locked = false;
    try {
        // tryLock(waitTime, unit): 不显式传 leaseTime —— 看门狗才会启动自动续期(默认30s)
        // (显式传 leaseTime=10 时看门狗不启动, 锁 10s 后硬性释放, 业务长于10s会锁失效)
        locked = lock.tryLock(3, TimeUnit.SECONDS);          // 等待3s 拿锁, 拿不到返回 false
        if (locked) {
            Stock s = stockRepo.find(skuId);
            if (s.count < 1) throw new BizException("库存不足");
            stockRepo.deduct(skuId);                          // 临界区: 扣库存
        }
    } finally {
        // 必须 finally 释放, 且只释放"当前线程确实持有"的锁
        if (locked && lock.isHeldByCurrentThread()) lock.unlock();
    }
}
```
> **该怎么做**：用 Redisson，**不显式传 leaseTime 才会自动续期（看门狗）**；`finally` 里**先判 `isHeldByCurrentThread()` 再 unlock**（避免 `tryLock` 失败时对未持有锁调用 unlock 抛异常）。
> **不该怎么做**：`setnx+expire` 拆步；显式传 `leaseTime` 却以为会续期；不判持锁直接 unlock。

## 主从 / 哨兵 / Cluster（Redis 的高可用）

### 1. 主从复制 + 哨兵（自动故障转移）
```yaml
# 哨兵: 自动故障转移(主挂了从顶上) —— 生产标配
sentinel monitor mymaster 10.0.0.11 6379 2   # quorum=2: 2个哨兵确认才判主"客观下线"
# 作用: ① 主从读写分离(分担读) ② 主故障自动选新主(高可用)
# 注意: quorum 只是"下线门槛", 真正完成 failover 还需哨兵之间的多数派(majority)选举新主
```
```bash
# 查看复制状态
redis-cli -h slave -p 6379 info replication   # role:slave, 看 master_link_status 是否 up
```
> **该怎么做**：生产用**主从 + 哨兵**（自动切换）；读多写少做主从分摊读压力。
> **不该怎么做**：单机 Redis 裸奔当核心存储——宕机即丢数据（未落盘部分）。

### 2. Cluster（分片，单机内存不够时）
```bash
# 集群: 数据散到多个节点(16384 槽), 客户端连错节点收 MOVED 重定向
# 注意: --cluster-replicas 1 需要"master数×(1+replica数)"个节点, 3主3从用6个节点
redis-cli --cluster create \
 10.0.0.11:6379 10.0.0.12:6379 10.0.0.13:6379 \
 10.0.0.14:6379 10.0.0.15:6379 10.0.0.16:6379 --cluster-replicas 1
```
> **该怎么做**：单机内存不够 / 需要横向扩展时用 Cluster（含副本，高可用）。
> **该怎么做**：一致性——多用 `Hash Tag` 把相关 key（如 `{user:1}:cart`）放同一 slot，保证同 slot 事务/范围操作。
> **注意**：`--cluster-replicas 1` 需要**节点数为 master×2**（本例 3 主 3 从 = 6 节点）；`Hash Tag` 惯用写法是把整个 tag 放 `{}`（如 `{user:1}`，而不是只有 `{1}`），让同一用户的多个 key 落在同一 slot。

### 3. 主从 vs 哨兵 vs Cluster 怎么选
| 方案 | 解决什么 | 适用 |
|-|-|-|
| 主从复制 | 读扩展 | 读多写少 |
| 主从 + 哨兵 | 读写扩展 + 自动切换 | 大多数业务 |
| Cluster | 容量扩展 + 分片 | 单机内存不够 |

## 持久化选型（数据安全）
| 方案 | 机制 | 数据安全 | 该用哪 |
|-|-|-|-|
| RDB | 快照落盘 | 可能丢最后一分钟 | 容忍少量丢失 |
| AOF | 追加日志 | 更持久（everysec） | 数据安全优先 |
> **该怎么做**：数据安全优先用 AOF（everysec 折中）；能容忍分钟级丢失用 RDB（恢复快）。
> **不该怎么做**：只开 RDB 且不设快照频率——宕机可能丢大量数据。

## 场景与红线（怎么做 / 不该怎么做）

| 场景 | ✅ 该怎么做 | ❌ 不该怎么做 |
|-|-|-|
| 缓存热点数据 | Redis + TTL + 随机扰动 | 缓存当唯一数据源 |
| 计数器/点赞 | `INCR` 原子自增 + 定时落库 | 每次写 MySQL |
| 排行榜 | ZSet（score 排序） | List 手动排序 |
| 分布式锁 | Redisson + finally unlock | `setnx` 拆步 |
| 会话存储 | Redis TTL | 存本地内存（多实例不同步） |
| 扣库存原子性 | Lua 脚本 | `GET`→`DECR` 三步 |
| 大 key | 拆分/分段 | 单 key 巨大（阻塞/内存） |
| 缓存一致性 | 先更 DB 再删缓存 | 先删缓存再更 DB |

## 红线小结（必背）

1. **Redis 不是主数据库**：核心业务数据的唯一事实源是 MySQL/PG。Redis 挂了最多丢缓存。
2. **所有 key 设 TTL**：+ 随机扰动防止雪崩；配 `maxmemory-policy`。
3. **原子操作用 Lua**：判断+扣减等一个脚本完成，别 `GET`→`DECR` 三步。
4. **批量用管道**：省网络往返，吞吐大幅提升。
5. **分布式锁必须 Redisson + finally unlock**：看门狗续期 + finally 防死锁。
6. **主从 + 哨兵是生产标配**：单机别当核心存储。
7. **数据安全优先用 AOF**：别只开 RDB 不设频率。

## 进阶自测

- [ ] 能说出 String/Hash/List/Set/ZSet 各自最典型的场景与底层结构
- [ ] 能复现并解决缓存穿透 / 击穿 / 雪崩
- [ ] 能写出 Cache-Aside 的正确更新顺序，并解释为什么
- [ ] 能用 Lua 脚本实现「判断+扣减」的原子操作
- [ ] 能用管道做批量操作，说明省了什么
- [ ] 能用 Redisson 实现带看门狗 + finally 释放的分布式锁
- [ ] 能说清主从 / 哨兵 / Cluster 的差异，以及 RDB / AOF 的取舍
