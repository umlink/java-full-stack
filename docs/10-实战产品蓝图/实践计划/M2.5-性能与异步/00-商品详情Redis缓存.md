# M2.5-00：商品详情 Redis 缓存

返回 [实践计划与进度](../README.md)。

## 目标

登录用户重复读取同一上架商品详情时可命中 Redis；商品上下架或 SKU 改价成功后缓存失效，缓存故障不改变原有商品接口行为。

## 范围

- 只缓存 `GET /products/{id}` 的成功详情，数据库仍是商品事实的唯一权威来源。
- 使用稳定商品详情 key、可配置 TTL 和显式开关；默认关闭，便于无 Redis 环境启动和回退。
- 商品状态或 SKU 价格事务提交后删除对应商品详情缓存。
- Redis 读写失败时记录告警并直查数据库；不缓存不存在、草稿或下架商品。
- 记录缓存读取的 `hit`、`miss`、`error` 与缓存失效结果，可经 Actuator metrics 查看。
- 使用本机真实 Redis 验证命中、TTL、失效、降级和指标，并执行后端全量回归。

## 契约与最小场景

- 输入输出：HTTP 方法、路径、鉴权、`Result<ProductDetailResponse>` 和既有错误码保持不变。
- 缓存键：`bootmall:catalog:product-detail:{productId}`，所有者是 `catalog`。
- 一致性：读未命中后回填；写事务提交后删除。删除失败允许在 TTL 内短暂陈旧，并以错误指标和日志暴露。
- 最小测试：首次 miss 后回填、再次 hit 不查库；不存在商品不回填；改价和上下架提交后 key 被删除；Redis 不可用时查询仍成功；指标可读取。

## 学习点

Cache-Aside、JSON 序列化、TTL、事务提交后失效、缓存降级、Micrometer 指标与真实 Redis 集成测试。

## 验收

1. 开启缓存后，同一商品详情首次查询产生 miss 并写入带 TTL 的 JSON，第二次查询产生 hit。
2. SKU 改价或商品上下架成功提交后，对应详情 key 删除；下一次读取返回数据库新值或原有 404。
3. Redis 不可用时，已有商品仍由数据库返回，失败被计入指标且不泄漏基础设施异常。
4. Actuator 能读取商品缓存请求与失效指标；不新增公开业务接口。
5. 真实 Redis 集成测试、架构测试和 `mvnw.cmd -pl services/user-service -am clean test` 通过，`git diff --check` 通过。

## 不做

- 不缓存商品列表、空值或跨模块报价查询。
- 不做 Caffeine 多级缓存、互斥重建、随机 TTL、布隆过滤器或分布式锁。
- 不迁移购物车，不做 Redis Lua 库存预扣、RocketMQ、延时关单或对账。
- 不改变商品 HTTP 契约、表结构、权限规则或错误码。

## 完成记录

日期：2026-10-10

提交：未提交（按本轮开发约束保留在工作区）。

测试与实现证据：

- 商品详情读取已接入 `ProductDetailsCache` 查询端口：Redis miss 后读取数据库并以 JSON 回填，后续 hit 不再调用查询仓储；不存在、草稿或下架商品不缓存。缓存默认关闭，开启开关、Redis 主机/端口/超时和 10 分钟默认 TTL 均从 `application.yml` 的环境变量占位读取。
- SKU 改价和商品上下架经独立命令端口登记失效动作，Redis 删除只在数据库事务提交后执行；回滚不删除缓存。稳定 key `bootmall:catalog:product-detail:{productId}` 和三个指标名由 `RedisProductDetailsCache` 统一持有。
- Redis 读写或删除异常会记录告警；读取异常回退数据库，写入异常继续返回数据库结果，删除异常等待 TTL 收敛。`bootmall.catalog.cache.requests`、`bootmall.catalog.cache.writes` 和 `bootmall.catalog.cache.invalidations` 以有限结果标签记录命中、未命中、错误、写入和失效。
- 本机启动 Redis 3.0.504（仅监听 `127.0.0.1:6387`，关闭持久化）执行真实 HTTP 集成测试：首次读取写入 JSON 且 TTL 在 1 至 120 秒内，第二次产生 hit；改价后 key 删除并读到新价格，下架后 key 删除且详情返回 404；认证后可从 Actuator metrics 读取 `hit/miss` 与 `deleted` 标签。
- JDK 25.0.4.1 下以真实 Redis 执行 `boot-server/mvnw.cmd -q -pl services/user-service -am clean test`：33 份 Surefire 报告共 142 个测试，失败、错误、跳过均为 0；新增 5 个测试覆盖缓存复用、空值不缓存、故障降级、提交后失效和真实 Redis。测试进程沿用系统临时目录中的 TCP 回退 Java agent，未修改仓库或用户级 Java/Maven 配置。
- `git diff --check` 通过。静态值审计确认：可部署值全部来自 Spring 配置源；缓存 key 与指标名由缓存适配器集中拥有；HTTP 路由、映射注解、测试数据和 JSON 断言继续按职责就近保留；本卡未改变 MVC 前缀、业务错误码、权限码或数据库结构。
