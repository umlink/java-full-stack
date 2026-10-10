# ADR-001：商品详情采用可降级的 Redis 旁路缓存

日期：2026-10-10
状态：已采纳

## 决策

在 M2.5-00 为 `catalog` 模块的上架商品详情查询引入 Redis Cache-Aside（旁路缓存）。读取先查 Redis，未命中时查数据库并以 JSON 写回；商品上下架或 SKU 改价事务成功提交后删除对应商品详情 key。数据库继续是商品事实的唯一权威来源，不缓存不存在或不可售商品。

缓存由 `app.catalog-cache.enabled` 显式开启，商品详情 TTL 由 `app.catalog-cache.product-detail-ttl` 配置。稳定 key 为 `bootmall:catalog:product-detail:{productId}`，由 `catalog` 模块独占。命中、未命中、读取错误和失效结果通过 Micrometer 指标暴露。Redis 连接、序列化、写入或删除失败时记录告警并回退数据库，不让缓存故障改变既有 HTTP 契约。

Spring Boot 4.0.0 BOM 管理 Spring Data Redis、Lettuce、Actuator 与 Micrometer 的兼容版本；业务模块只声明对应 starter。缓存值使用项目现有 Jackson JSON 映射，不使用 Java 原生序列化。

## 触发信号与备选

M2.5 的里程碑 DoD 明确要求缓存命中和失效可观察；当前商品详情每次请求都会读取 H2，已经具备可重复读且允许短暂陈旧的缓存边界。

- 保持只读数据库：依赖最少，但无法交付本里程碑的缓存行为与观测证据。
- 进程内 Caffeine：可减少单实例数据库读取，但多实例各自持有副本，不能为后续购物车、Lua 库存预扣复用 Redis 运行基线。
- Redis 旁路缓存：增加一个运行依赖，但保持数据库权威、可关闭和可降级，且能用真实 Redis 验证跨进程存储与 TTL。

本卡不引入多级缓存、互斥重建、空值缓存或分布式锁；没有热点压测证据前，这些复杂度不成立。

## 影响与回退

运行环境需要可访问的 Redis。应用默认关闭商品缓存；开启时若 Redis 不可用，详情读取会直查数据库，代价是响应变慢和数据库负载上升。删除失败可能在 TTL 窗口内读到旧详情，指标和告警用于发现该窗口，后续卡片可在有证据时增加重试或事件补偿。

回退只需关闭 `BOOT_CATALOG_CACHE_ENABLED` 并重启应用；读写 API、数据库结构和商品数据无需迁移。若彻底移除，可删除两个 starter、缓存适配器和配置，应用端口仍可由无缓存实现满足。
