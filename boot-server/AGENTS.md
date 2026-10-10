# boot-server/AGENTS.md

本文件定义 `boot-server/` 后端工程规范。开发时先看第 2 节的开工门槛与 DDD 边界，再按本卡涉及的 Web、持久化、事务、安全和测试章节执行；新技术接入看第 15 节。

## 1. 技术基线

- Java 25。
- Spring Boot 4.0.0。
- MyBatis-Plus 3.5.17。
- Maven 使用本目录 `mvnw` / `mvnw.cmd`。
- 后端包名前缀：`com.example.bootserver`。
- 依赖版本：Spring Boot BOM 已管理的依赖只在使用模块声明；BOM 未管理的第三方依赖在父 pom `dependencyManagement` 集中锁定版本，子模块不重复写版本。

常用命令：

```bash
mvnw.cmd -pl services/user-service -am test
mvnw.cmd -pl services/user-service -am install -DskipTests
mvnw.cmd -pl services/user-service spring-boot:run
```

`spring-boot:run` 不带 `-am`。

## 2. 模块职责

```text
boot-server/
  infrastructure/common-core/   纯 Java 公共能力
  services/user-service/        当前模块化单体部署单元
```

### 当前模块职责

- `common-core` 只放领域无关能力，例如 `Result`、`ErrorCode`、`BusinessException`。
- `common-core` 禁止依赖 Spring MVC、数据库、业务服务。
- HTTP 接口、DTO、`@RestControllerAdvice`、安全配置放服务模块。
- 应用服务只编排用例、事务和领域对象；领域规则与状态迁移由聚合根、实体或值对象守护。
- Mapper 只负责数据访问。
- Controller 禁止拼装复杂业务失败分支。

### 领域模块化单体

`user-service` 按数据所有权和业务语言划分六个领域模块：

| 模块 | 数据所有权与职责 |
|-|-|
| `identity` | 用户、角色、权限、注册登录、JWT 与后台用户管理 |
| `catalog` | 商品、SKU、上下架、改价与对外报价 |
| `cart` | 当前用户购物车条目与数量规则 |
| `inventory` | SKU 库存总量、锁定量与条件预扣 |
| `order` | 订单、订单明细、幂等与订单状态 |
| `payment` | 支付单、模拟支付与库存结算编排 |

每个模块使用同一骨架：

```text
<module>/
  api/                         跨模块唯一可依赖的公开契约
  internal/
    interfaces/                HTTP、安全过滤器与协议转换
    application/
      command/                 写用例、事务与跨模块编排
      query/                   读用例、查询端口与场景投影
    domain/                    聚合、值对象、领域失败、写仓储端口
    infrastructure/            MyBatis、配置绑定与端口实现
```

- 其他模块只能依赖目标模块的 `api` 包，禁止 import 目标模块的 `internal`。模块没有跨模块消费者时，`api` 可只保留 `package-info.java`，不得为了形式暴露内部类型。
- `api` 只放跨模块确实需要的接口、命令和结果；不得暴露 Web DTO、ORM Entity、Mapper、框架类型或 `internal` 类型。实现 Bean 放在本模块 `internal`。
- 模块内部依赖方向为 `interfaces -> application -> domain`，`infrastructure -> application/domain`。应用层不得依赖接口层、Mapper 或 ORM Entity；领域层不得依赖 Spring、MyBatis-Plus、HTTP、Bean Validation 或数据库实现。
- 一个模块独占写入自己的表，也不得直接查询其他模块的表。跨模块读取和写入均经公开 API；当前单库、本地方法调用不改变该规则。
- 根包只保留启动入口、全局异常转换与真正跨领域的框架配置。`common-core` 仍只放领域无关契约，不放任何领域 DTO、Entity 或服务接口。
- 架构测试必须覆盖：跨模块只依赖 `api`、模块无环依赖、四层方向、领域层框架隔离。目录迁移或新增跨模块协作时同步更新测试。

### 轻量 CQRS

- 命令与查询在应用层分开组织；写侧加载领域模型并守住不变式，读侧按接口场景返回专用投影，不为查询强行恢复聚合。
- 轻量 CQRS 继续共用当前数据库和本地事务；它不等于主从复制，也不自动引入事件溯源、独立读库、消息或最终一致。
- 查询端口定义在 `application/query`，由 `infrastructure` 实现；命令仓储端口优先定义在 `domain`，由 `infrastructure` 实现。
- 跨模块命令或查询只能经对方 `api`。例如订单通过 `catalog.api` 取得可下单报价，通过 `inventory.api` 条件预扣库存；购物车价格同样由 `catalog.api` 提供，禁止 Mapper 跨表读取商品数据。

### 开工门槛

- 编码前先读 [实践计划与进度](../docs/10-实战产品蓝图/实践计划/README.md) 的当前卡及其前置，列出本卡涉及的业务上下文、不变式、数据所有者、输入输出、失败分支和最小测试场景。
- 缺少必要规则时先补本文件；架构取舍记入 [ADR](../docs/10-实战产品蓝图/adr/README.md)，卡片专属契约留在卡片中。

### DDD 边界与依赖

- 后端业务代码实现必须严格遵循 [DDD 领域驱动设计](../docs/04-阶段四-微服务架构与分布式系统/04-DDD领域驱动设计.md)：先明确限界上下文和聚合边界，再按接口层、应用层、领域层、基础设施层组织职责。
- 领域层不得依赖 Spring、MyBatis-Plus、HTTP 或数据库实现；持久化由基础设施层实现领域定义的仓储契约。新增行为及修改触及的既有行为均按此规则落实，不借单卡改动重构无关代码。
- 业务包按上方六个领域模块组织，对外只暴露 `api`；一个上下文不得直接读写另一个上下文的持久化数据。当前单体中的跨上下文协作也须有显式契约，不以未来拆服务为由提前增加物理模块。
- 接口 DTO 不进入领域层。接口层负责协议绑定和响应转换，应用层负责用例编排与事务，领域层负责状态和不变式，基础设施层负责数据库、缓存、消息及外部调用；依赖方向指向领域层。
- 聚合根是聚合内状态变更的入口；值对象不可变并按值判等。领域实体与 MyBatis-Plus 持久化实体分别建模，仓储实现负责两者转换，不能用 ORM 注解或公开 setter 绕过领域规则。
- 普通列表与搜索可以使用只读查询投影，不为读取强行加载写模型聚合。

## 3. Java 命名

- 类名：`UpperCamelCase`。
- 方法、字段、变量：`lowerCamelCase`。
- 常量：`UPPER_SNAKE_CASE`。
- 缩写按单词处理：`userId`、`jwtToken`、`apiUrl`。
- 请求 DTO：`XxxRequest`。
- 响应 DTO：`XxxResponse`。
- 异常：`XxxException`。
- 配置绑定：`XxxProperties`。
- 测试类：`XxxTest`。

方法命名：

- 单个读取：`getXxxBy...`。
- 多项列表：`listXxx...`。
- 模糊或条件搜索：`searchXxxBy...`。
- 创建：`createXxx`。
- 更新：`updateXxx`。
- 删除：`deleteXxx`。
- 布尔判断：`isXxx`、`hasXxx`、`canXxx`。
- 转换：`toXxx`。

禁止使用 `doXxx`、`handleXxx`、`processXxx` 表达业务公开方法。

## 4. Web 与统一响应

- 所有 API 响应使用 `Result<T>`，字段固定为 `code`、`message`、`data`。
- `code = 0` 表示成功。
- 失败业务码统一来自 `ErrorCode`。
- 业务代码、异常处理器、测试禁止直接写业务码数字。
- HTTP 状态码表达通信语义，业务码表达客户端处理分类。
- MVC 前缀只由 `spring.mvc.servlet.path` 管理；Controller 禁止重复写 `/api`。
- 写接口必须使用请求 DTO，禁止直接暴露实体作为外部写入模型。

### 静态值与路径治理

- `spring.mvc.servlet.path`、端口、数据库地址、JWT TTL、密钥、管理员环境键等可部署配置只能从配置绑定对象或 Spring 配置源读取；生产代码和覆盖该配置的真实 HTTP 测试不得复制默认值。测试验证前缀可变性时，必须覆盖一个非默认测试配置，并通过绑定后的属性组装外部 URL。
- Controller 的 `@RequestMapping`、`@GetMapping`、`@PostMapping`、`@PutMapping`、`@DeleteMapping` 是接口定义，允许并要求就近保留清晰的内部资源路径字面量，例如 `/auth`、`/register`、`/users/{id}`；禁止仅为常量治理把它们替换为路径常量。Security 配置中的 DispatcherServlet 内部匹配路径同理，保持 `/auth/**`、`/users/**` 的局部可读性，禁止拼接 MVC 外部前缀。
- 稳定的跨层机器协议、成功码、错误码、权限码、角色码、业务状态和共享存储键必须按职责集中。优先使用已有领域状态类型或窄作用域命名常量，例如 `ErrorCode`、`Result.SUCCESS_CODE`；持久化状态值由仓储映射，不把 MyBatis-Plus Entity 当作领域契约。全局策略或阈值仅在确有多个消费者且需独立演进时集中。禁止无语义的 `Constants`、`CommonUtil` 或跨领域大杂烩。
- 能变化的配置值和稳定不变的契约值不可混用：配置绑定类只保存配置，领域类型只保存领域不变量，路由注解只声明接口，测试辅助方法只负责根据绑定配置生成外部地址。
- DTO 的 Bean Validation 注解及其局部边界数值、正则和提示语必须就近保留；即使多个 DTO 使用相同规则，也不为此创建校验常量类。Controller 与 Security 路径、单测 fixture/request/assertion、SQL 表名/列名及 `schema.sql` 种子业务码均可保留字面量；它们不得成为生产契约的第二份定义。字符串重复不是抽常量条件，只有跨模块共享、可配置或承载机器协议，且集中后能明确所有权与演进一致性的值才集中。

静态审计仅在本次改动涉及配置值、错误码、权限码、存储键时执行，至少覆盖：

```bash
rg -n --glob '*.java' --glob '*.yml' --glob '*.yaml' --glob '*.properties' "/api|servlet.path|timeout|ttl|TOKEN|SECRET|BOOT_|Authorization|Bearer " boot-server
rg -n --glob '*.java' 'static final|@RequestMapping|@GetMapping|@PostMapping|@PutMapping|@DeleteMapping' boot-server
```

改动涉及错误码或权限码时，另扫对应数字与码值（如 `40000|40100|40300|40400|40900|50000|user:manage`）。

审计结论并入对应卡片的完成记录，说明：运行时配置从哪一份配置源读取；稳定常量由哪个职责类型拥有；哪些字面量因映射注解、Security 内部路径、DDL/种子数据、协议固定字段或单测试数据而保留；以及配置前缀变化是否有测试证据。不单独成文。

错误码约定：

```text
40000 参数错误
40100 未认证
40300 无权限
40400 不存在
40900 冲突
50000 系统错误
```

## 5. 异常处理

- 领域失败只表达业务原因，不携带 HTTP 状态、接口错误码或客户端文案。
- 应用层或接口适配层将领域失败转换为 `BusinessException`；领域层不得依赖 `ErrorCode` 的 HTTP 分类。
- Web 层统一转换异常为 `Result<T>`。
- 未知异常必须记录完整日志。
- 响应禁止暴露堆栈、SQL、类名、配置、密钥。
- 参数校验错误统一返回 `400/40000`。
- 不存在资源统一返回 `404/40400`。

## 6. 认证与授权

- 新增接口必须明确归类：公开、仅认证、权限保护。
- 管理接口默认最小授权。
- `401` 表示无身份或身份无效。
- `403` 表示身份有效但权限不足。
- Security Filter Chain 的 `AuthenticationEntryPoint` 和 `AccessDeniedHandler` 必须写统一 `Result`。
- `GrantedAuthority` 使用稳定权限码，例如 `user:manage`。
- 权限码不得使用数据库主键、中文展示名、易变角色名称。
- JWT 只保存主体身份和有效期。
- 每个受保护请求从当前用户角色权限关系加载 authority。
- 不把角色或权限列表写入 JWT 后作为唯一事实来源。

授权测试至少覆盖：

- 无身份：`401/40100`。
- 普通用户：`403/40300`。
- 有权限用户：成功。

## 7. MyBatis-Plus 规范

- MyBatis-Plus Entity 只描述持久化结构，不承载接口请求语义或充当领域聚合。
- Entity 字段使用包装类型，禁止基本类型表达数据库可空字段。
- 表名使用 `@TableName` 明确声明。
- 主键使用 `@TableId` 明确声明。
- 逻辑删除字段统一为 `deleted`，通过 MyBatis-Plus 逻辑删除配置处理。
- Mapper 继承 `BaseMapper<Entity>`，只声明必要的自定义 SQL。
- MyBatis-Plus 的 Service 能力只用于基础设施数据访问实现，不作为承载领域规则的应用服务基类。
- `ServiceImpl` 包路径以当前 MyBatis-Plus 版本实测为准。
- 分页必须走 MyBatis-Plus 分页插件，不手写重复分页拼接。
- 查询条件优先使用 `LambdaQueryWrapper`，避免字符串字段名。
- 更新条件优先使用 `LambdaUpdateWrapper` 或先查后改，禁止无条件 update。
- `last()` 只能用于受控 SQL 片段，例如固定 `LIMIT`；禁止拼接用户输入。
- Wrapper 条件必须显式处理空值，禁止把空字符串、空集合误当有效条件。
- 批量操作必须限制批次大小。

禁止：

- Controller 直接调用 Mapper。
- Mapper 返回 Map 承载长期业务契约。
- Entity 直接作为新增、更新请求体。
- 在 Service 中散落硬编码 SQL。
- 使用字符串列名绕过重构检查。
- 未加条件的 `update` / `delete`。

## 8. 手写 SQL 规范

手写 SQL 只用于 MyBatis-Plus 难以清晰表达或性能要求明确的场景。

允许场景：

- 多表查询且结果为明确 DTO。
- 明确需要数据库函数、窗口函数、CTE。
- 批量写入、复杂统计、报表查询。
- 经执行计划确认的性能优化。

基本要求：

- SQL 必须使用参数绑定，禁止字符串拼接用户输入。
- XML SQL 使用 `#{}` 绑定参数，禁止用 `${}` 承接外部输入。
- 返回字段必须显式列出，禁止 `SELECT *`。
- 表必须使用清晰别名。
- 查询必须考虑逻辑删除条件。
- 分页查询必须有稳定排序。
- 排序字段来自白名单，禁止直接透传前端字段。
- 大查询必须限制返回行数。
- 批量修改必须有明确 where 条件。
- 涉及索引选择时，在注释或测试中保留原因。

XML 规范：

- Mapper XML 与 Mapper 接口同名。
- `namespace` 必须指向 Mapper 全限定名。
- `id` 必须与 Mapper 方法名一致。
- `resultMap` 用于复杂映射；简单 DTO 可用 `resultType`。
- 动态 SQL 条件顺序保持稳定：等值、范围、模糊、排序、分页。
- `like` 查询必须说明是否允许前缀模糊；默认禁止左模糊。

性能红线：

- 禁止无索引大表模糊查询。
- 禁止在线接口执行无边界聚合。
- 禁止深分页直接暴露给高频接口。
- 禁止在 where 字段上包函数导致索引失效，除非有函数索引或明确验收。
- 禁止 N+1 查询；需要批量加载或 join。

## 9. 事务规范

- 写业务需要原子性时必须显式使用 `@Transactional`。
- `@Transactional` 放在应用层用例入口；领域层不依赖 Spring 事务注解。
- 事务方法放在 Spring Bean 的 public 方法上。
- 禁止同类内部自调用依赖事务生效。
- 事务内禁止调用慢外部接口。
- 事务内禁止执行不可控长循环。
- 捕获异常后仍需回滚时，必须重新抛出或显式标记 rollback。
- 只读查询可使用 `@Transactional(readOnly = true)`，但不作为性能优化万能手段。

### 并发与幂等

- 写入前明确业务不变式与并发冲突的最终守护者：领域对象守护合法状态，数据库唯一约束、条件更新或乐观锁守护并发下的数据结果；不能仅靠先查再写。
- 对加购、库存预扣、改价等并发写入，定义更新条件、受影响行数为零和唯一键冲突的语义，且只对明确可重试的冲突做有界重试；不得吞掉异常后声称成功。
- 对下单、支付模拟、消息消费等可能重复触发的动作，先定义稳定的业务幂等键、作用范围和重复请求响应；去重记录与业务副作用应在同一可靠边界内提交。
- 状态迁移由领域对象定义允许的前后状态；数据库更新仍须带当前状态或版本条件，防止并发请求绕过迁移规则。

## 10. 数据库与配置

- `schema.sql` 必须可重复执行。
- 初始化数据不得覆盖已有学习数据。
- H2 文件库路径依赖服务模块工作目录：`services/user-service` 下的 `../../data/bootapp` 指向 `boot-server/data/`。
- 移动模块前必须审计 H2 相对路径和文档链接。
- `boot-server/data/*.lock.db`、`*.trace.db` 禁止提交。
- `boot-server/data/bootapp.mv.db` 未经用户明确要求不得暂存。
- 密钥、密码、token 只从环境变量或安全配置注入，不写入代码和文档示例真实值。
- 从 H2 脚本初始化转向真实数据库或多服务独立数据源前，选定一种版本化迁移机制并迁移既有脚本；禁止把 `schema.sql` 的每次启动执行与迁移工具混用。测试与运行环境使用同一迁移来源，测试数据另行管理。

### M2 交易数据建模

- 金额字段必须使用 `DECIMAL(precision, scale)` 与 Java `BigDecimal`，明确精度和小数位；禁止 `float`、`double`。
- 新表字段必须显式定义 `NOT NULL`、默认值和状态可取值；业务状态由所属领域类型集中维护，数据库取值由仓储映射，不散落魔法数字。
- 业务编码（例如 SKU 编码）必须由数据库唯一约束兜底；逻辑删除表要在建模时明确唯一键是否复用，并用索引结构实现该策略。
- 关联字段必须声明引用关系或在不使用物理外键时记录替代一致性机制；删除策略、乐观锁字段和必要索引在 DDL 与测试中同时体现。
- Schema 变更保持幂等，种子数据按稳定业务键防重，不覆盖已有学习数据。

## 11. OpenAPI

- OpenAPI 元信息放单独配置类。
- Controller 使用 `@Tag` 表达资源分组。
- 公开接口不声明安全要求。
- 受保护接口明确 Bearer JWT 安全方案。
- 描述必须与 HTTP 方法、路径、权限、`Result<T>` 响应一致。
- 新增或调整文档配置时，必须测试 OpenAPI JSON 和 Swagger UI 可访问。

## 12. 测试

- 后端代码变更至少运行受影响服务测试。
- 新增接口补 Web 层测试。
- 新增错误处理补 HTTP 状态和 `Result` JSON 测试。
- 新增授权规则补 401、403、成功三类测试。
- 新增 Mapper 自定义 SQL 必须有集成测试或等价数据访问测试。
- 新增或修改领域规则补不依赖 Spring 和数据库的领域测试；改动上下文边界或依赖方向时补可重复执行的架构约束测试。
- 测试覆盖非法状态、重复请求、并发竞争、事务回滚和数据最终不变量；具体并发数量及接口响应以当前卡片验收为准。
- 测试方法名表达场景和预期。

## 13. 注释

- 公共类、接口、枚举、可复用方法写 Javadoc。
- 框架机制首次出现时说明谁调用、何时调用、为什么放在该层。
- 对事务、权限、异常转换、隐式 ORM 行为写必要说明。
- 注释必须解释边界和原因，不重复代码字面含义。
- 过期注释必须随实现删除或更新。

## 14. 通用编码约定

- 禁止 `Executors.newFixedThreadPool` / `newCachedThreadPool`，一律显式 `new ThreadPoolExecutor`（对齐阶段零 02 讲阿里规约）。
- POJO 属性使用包装类型。
- 日志使用 `{}` 占位符，禁止字符串拼接。
- Bean 优先构造器注入。
- 命名驼峰语义化。

## 15. 新技术与服务化能力接入

### 准入与依赖

- 仅在卡片范围和里程碑已解锁时新增依赖、表、模块或运行服务。引入 Redis、MQ、数据库迁移、网关、注册发现、服务间一致性等结构性能力前，先记录 ADR：触发信号、备选、版本兼容、数据所有权、故障语义和回退办法。
- 引入新的 Spring Boot、Spring Cloud、Spring Cloud Alibaba 或第三方 starter 时，核对其官方兼容矩阵与传递依赖；BOM 按父工程统一管理，实际使用模块再声明依赖，并用构建和启动验证。不得只凭教程示例拼接版本。

### 缓存与消息

- 引入缓存前定义数据库与缓存各自的权威范围、键所有者、过期/失效规则、并发重建、缓存不可用时的行为及验证指标；缓存不得成为无回退的数据真相。
- 引入 MQ 前区分领域事件与对外集成事件，定义事件版本、业务幂等键、重试上限、失败处置和可追踪性；消费端按可能重复投递设计，不依赖“恰好一次”的假设。业务事务与发件一致性按里程碑选择 Outbox 等方案。

### 服务化与运行

- 拆服务前明确每个上下文的数据所有权、对外接口和依赖方向；禁止跨服务共享表写入、物理外键或将领域模型放进公共模块。跨服务鉴权、超时、重试和补偿须逐一确定，且避免在本地事务内同步等待慢远程调用。
- 引入网关、异步处理或多实例运行后，补健康检查、关键业务指标、关联日志与链路追踪规则；压测与故障演练按对应里程碑验收，不提前为单机卡片引入整套治理依赖。
