# M2-A-00：领域模块化单体与轻量 CQRS

返回 [实践计划与进度](../README.md)。

## 目标

保持现有 HTTP、事务、鉴权和数据库行为不变，把 `user-service` 一次性迁移为按领域组织的模块化单体，并用自动化架构测试阻止边界回退。

## 范围

- 建立 `identity`、`catalog`、`cart`、`inventory`、`order`、`payment` 六个领域模块。
- 每个模块只通过 `<module>/api` 向其他模块公开契约；实现全部进入 `<module>/internal`。
- 模块内部统一使用四层：`interfaces`、`application`、`domain`、`infrastructure`。应用层按需要继续分为 `command` 与 `query`。
- 采用轻量 CQRS：写侧通过领域模型守不变式，读侧使用场景化投影；读写继续共用当前数据库和本地事务，不引入事件溯源、独立读库或异步同步。
- `catalog` 公开 SKU 是否存在与可下单报价查询；`inventory` 公开库存预扣、结算和释放命令；`order` 公开支付认领命令。订单、购物车和支付只能通过这些 API 协作，禁止依赖对方内部类或直接读取对方数据表。
- 将现有生产代码和对应测试迁移到新目录，删除旧的横向 `controller`、`service`、`mapper`、`entity`、`security`、`stock` 包结构。
- 引入测试作用域的 ArchUnit，验证模块公开 API、四层依赖方向、领域层纯净和模块无环依赖。
- 更新 `boot-server/AGENTS.md` 的后端开发规范与 `product-tree.md` 的真实代码导航。

## 目标目录与依赖契约

```text
<module>/
├── api/                         跨模块唯一可见面；只放必要接口、命令和结果
└── internal/
    ├── interfaces/              HTTP、安全过滤器和协议转换
    ├── application/
    │   ├── command/             写用例、事务与跨模块 API 编排
    │   └── query/               读用例、查询端口与场景化投影
    ├── domain/                  聚合、值对象、领域失败和写仓储端口
    └── infrastructure/          MyBatis、配置绑定和端口实现
```

- 模块内部依赖方向为 `interfaces -> application -> domain`，`infrastructure -> application/domain`；应用层和领域层不得反向依赖接口层或基础设施层。
- 一个模块依赖另一个模块时，目标包必须是对方的 `api`；公开 API 不得泄漏 `internal`、ORM Entity、Mapper 或 Web DTO。
- `domain` 不依赖 Spring、MyBatis、HTTP、Bean Validation 或数据库实现。
- 根包只保留启动类、全局异常转换和确属全局的框架配置；`common-core` 继续只承载领域无关契约。

## 迁移约定

- HTTP 方法、路径、请求响应 JSON、错误码、权限规则和 OpenAPI 语义保持不变。
- 表结构、种子数据和已有业务不变式保持不变；只允许为解除跨模块读表而调整查询实现。
- 购物车当前价格由 `catalog` API 批量补齐，不再在购物车 Mapper 中关联商品表。
- 身份、商品和订单模块将混合读写的 Service 拆为命令、查询用例和端口，但不借机新增功能。
- 迁移开始后远端主线已完成 M2-08 至 M2-11；变基时保留这些既有行为，并把新增的支付上下文一并迁入相同模块骨架。

## 验收

1. 六个领域模块全部落入目标目录，生产代码不存在绕过模块 API 的跨模块依赖，也不存在应用层反向依赖 Mapper、ORM Entity 或 Web DTO。
2. 现有注册登录、用户管理、商品管理、购物车、库存、下单幂等、订单查询、支付、取消和履约行为全部回归通过，接口契约不变。
3. ArchUnit 测试覆盖模块边界、四层方向、领域层框架隔离和模块无环依赖；故意越界会导致测试失败。
4. `mvnw.cmd -pl services/user-service -am test` 在 JDK 25 下通过，`git diff --check` 通过。
5. 后端规范和实现导航与迁移后的真实目录一致。

## 不做

- 不新增或改变支付、取消、发货、完成及其他业务行为；只迁移远端已经验收的实现。
- 不拆 Maven 模块或独立服务，不引入 Spring Modulith 运行时、消息、事件溯源、独立读库或缓存。
- 不修改外部 API 契约、数据库表结构或现有错误码。
- 不顺手重写无关算法、页面或学习讲义。

## 完成记录

日期：2026-10-10

提交：本次提交（`refactor: 完成M2-07并落地领域模块化单体`）。

测试与接口证据：在 `boot-server/` 使用 JDK 25.0.4.1 执行 `mvnw.cmd -q -pl services/user-service -am clean test`，30 份 Surefire 报告共 137 个测试，失败、错误、跳过均为 0；真实 HTTP、H2、并发、事务回滚、鉴权、下单幂等、订单查询、支付、取消和履约用例全部回归通过。`git diff --check` 通过。本机 Unix domain loopback 异常仍只在测试进程临时挂载位于系统临时目录的 TCP 回退 Java agent，未修改仓库或用户级 Java/Maven 配置。

- 目录与契约：生产代码和对应测试已迁入 `identity`、`catalog`、`cart`、`inventory`、`order`、`payment` 的 `api + internal/{interfaces,application,domain,infrastructure}` 结构；旧横向业务包已移除。跨模块协作收口为 `catalog.api.CatalogQueryApi`、`inventory.api.InventoryCommandApi` 与 `order.api.OrderPaymentApi`，公开 DTO 不泄漏内部、Web 或 ORM 类型。
- 数据所有权：购物车 Mapper 只访问 `t_cart_item`，商品名与现价经 `catalog.api` 批量补齐；订单经 `catalog.api` 获取可售报价、经 `inventory.api` 条件预扣，未新增跨模块读表或写表。
- 轻量 CQRS：六个模块的应用用例按命令/查询职责组织，读侧使用页面投影与查询端口，写侧通过领域模型和仓储端口守不变式；继续共用原数据库和本地事务，没有引入消息、独立读库、事件溯源或新业务行为。
- 架构防线：引入测试作用域 ArchUnit 1.5.1；`ModuleArchitectureTest` 自动检查模块只能位于 `api/internal`、跨模块不得依赖 `internal`、应用层不得反向依赖接口层/基础设施层、接口层不得直连基础设施、领域层不依赖框架或 HTTP 错误契约、命令与查询互不反向依赖、顶层包无循环。
- 契约与静态值：HTTP 方法、路径、请求响应 JSON、错误码、权限码、表结构和运行配置保持不变；ArchUnit 版本在父 POM 集中锁定，子模块只声明测试依赖。路由、校验文案、Mapper 表映射和测试数据仍按职责就近保留。
