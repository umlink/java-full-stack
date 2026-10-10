# 后端实现导航树

后端在一个 Spring Boot 部署单元内按 `identity`、`catalog`、`cart`、`inventory`、`order`、`payment` 六个领域模块组织。每个模块只通过 `api` 暴露跨模块契约，具体实现统一收在 `internal`，内部按接口层、应用层、领域层和基础设施层划分。卡片状态以[实践计划与进度](实践计划/README.md)为准。

```text
boot-server/
├── pom.xml                                  Maven 聚合工程；统一 Java 25、Spring Boot 与 ArchUnit 版本
├── infrastructure/common-core/
│   └── src/main/java/com/example/bootserver/common/
│       ├── result/Result.java                统一响应 code、message、data
│       └── error/                            稳定错误码与业务异常
└── services/user-service/
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/com/example/bootserver/
        │   │   ├── BootServerApplication.java
        │   │   ├── identity/                 账号、认证、JWT 与 RBAC
        │   │   │   ├── api/                  当前没有跨模块身份契约；保留明确公开边界
        │   │   │   └── internal/
        │   │   │       ├── interfaces/       注册登录、用户管理 HTTP；JWT 过滤器与授权配置
        │   │   │       ├── application/
        │   │   │       │   ├── command/      注册、登录校验、资料写入、令牌签发端口
        │   │   │       │   └── query/        用户投影、权限查询、令牌解析端口
        │   │   │       ├── domain/           用户账号、角色/权限码与命令仓储端口
        │   │   │       └── infrastructure/   MyBatis 适配器、JWT 实现、密码与管理员引导配置
        │   │   ├── catalog/                  商品与 SKU
        │   │   │   ├── api/                  CatalogQueryApi、OrderableSkuQuote、CartSkuSnapshot
        │   │   │   └── internal/
        │   │   │       ├── interfaces/web/   商品浏览、创建、上下架与 SKU 改价
        │   │   │       ├── application/
        │   │   │       │   ├── command/      商品/SKU 写用例与命令结果
        │   │   │       │   └── query/        商品页面投影、模块查询 API 实现与查询端口
        │   │   │       ├── domain/           Product、Sku 与 CatalogRepository
        │   │   │       └── infrastructure/
        │   │   │           ├── cache/         Redis 商品详情旁路缓存、事务后失效与指标
        │   │   │           └── persistence/   MyBatis 命令/查询适配器、Mapper 与表实体
        │   │   ├── cart/                     本人购物车
        │   │   │   ├── api/                  当前无跨模块购物车契约
        │   │   │   └── internal/
        │   │   │       ├── interfaces/web/   购物车 HTTP 与请求/响应 DTO
        │   │   │       ├── application/
        │   │   │       │   ├── command/      加购、改数量、删除与原子写步骤
        │   │   │       │   └── query/        购物车投影；经 catalog.api 补齐商品现价
        │   │   │       ├── domain/           CartItem、CartQuantity 与仓储端口
        │   │   │       └── infrastructure/   Redis Hash 权威存储、旧表惰性迁移与 MyBatis 回退适配器
        │   │   ├── inventory/                库存
        │   │   │   ├── api/InventoryCommandApi.java  预扣、结算和释放库存的公开命令契约
        │   │   │   └── internal/
        │   │   │       ├── interfaces/web/   库存设置与查询 HTTP
        │   │   │       ├── application/
        │   │   │       │   ├── command/      设置总量、预扣、结算、释放、条件写入与分批对账
        │   │   │       │   └── query/        库存查询投影与查询端口
        │   │   │       ├── domain/           Stock 不变式与仓储端口
        │   │   │       └── infrastructure/   Redis Lua 预占/对账副本、定时调度与库存 MyBatis 适配器
        │   │   ├── order/                    订单
        │   │   │   ├── api/                  OrderPaymentApi 与支付认领快照
        │   │   │   └── internal/
        │   │   │       ├── interfaces/web/   下单、查询、取消、确认收货与后台发货 HTTP
        │   │   │       ├── application/
        │   │   │       │   ├── command/      幂等下单、支付认领、取消与履约状态迁移
        │   │   │       │   └── query/        本人/后台订单投影与查询端口
        │   │   │       ├── domain/           订单聚合、状态机、请求指纹与仓储端口
        │   │   │       └── infrastructure/   订单命令/查询 MyBatis 适配器
        │   │   ├── payment/                  模拟支付
        │   │   │   ├── api/                  当前无跨模块支付契约
        │   │   │   └── internal/
        │   │   │       ├── interfaces/web/   本人支付 HTTP 与响应 DTO
        │   │   │       ├── application/command/ 支付编排、支付号与命令结果
        │   │   │       ├── domain/           Payment、PaymentStatus 与仓储端口
        │   │   │       └── infrastructure/   支付单 MyBatis 适配器
        │   │   ├── config/                    跨模块框架配置：MVC 路径、OpenAPI、MyBatis-Plus
        │   │   └── handler/                   全局 HTTP 异常转换
        │   └── resources/
        │       ├── application.yml            数据源、JWT、Redis、缓存、指标、MVC 路径与订单 worker ID
        │       └── db/schema.sql              用户/RBAC、商品、购物车、库存、订单与支付表
        └── test/
            ├── java/com/example/bootserver/
            │   ├── architecture/ModuleArchitectureTest.java
            │   │   跨模块公开 API、四层依赖、CQRS 分离与循环依赖约束
            │   ├── identity/internal/         身份 Web、认证、持久化与管理员引导回归
            │   ├── catalog/internal/          商品领域、持久化、缓存降级和真实 Redis/HTTP 回归
            │   ├── cart/internal/             购物车领域与真实 HTTP/并发回归
            │   ├── inventory/internal/        库存领域、架构、真实 Redis 预占/对账与 HTTP/并发回归
            │   ├── order/internal/            订单领域、架构、幂等与查询回归
            │   ├── payment/internal/          支付领域、架构与真实 HTTP 回归
            │   ├── M2RegressionIntegrationTests.java  M2 全路径回归
            │   ├── handler/                   统一异常响应测试
            │   ├── ApiPrefixIntegrationTests.java
            │   └── BootServerApplicationTests.java
            └── resources/application.properties  测试用固定假密钥
```

跨模块依赖保持单向：`cart -> catalog.api`、`inventory -> catalog.api`、`order -> catalog.api + inventory.api`、`payment -> order.api + inventory.api`。各模块拥有自己的表，查询侧可以针对页面建立投影，但不得直接读取其他模块的数据表。
