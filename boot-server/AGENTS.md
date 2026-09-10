# boot-server/AGENTS.md

本文件定义 `boot-server/` 后端工程规范。适用于 Spring Boot、MyBatis-Plus、SQL、测试和数据库配置。

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
  services/user-service/        当前业务服务
```

- `common-core` 只放领域无关能力，例如 `Result`、`ErrorCode`、`BusinessException`。
- `common-core` 禁止依赖 Spring MVC、数据库、业务服务。
- Controller、DTO、`@RestControllerAdvice`、安全配置放服务模块。
- Service 放业务规则。
- Mapper 只负责数据访问。
- Controller 禁止拼装复杂业务失败分支。

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

- 预期业务失败抛 `BusinessException`。
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

- Entity 只描述持久化结构，不承载接口请求语义。
- Entity 字段使用包装类型，禁止基本类型表达数据库可空字段。
- 表名使用 `@TableName` 明确声明。
- 主键使用 `@TableId` 明确声明。
- 逻辑删除字段统一为 `deleted`，通过 MyBatis-Plus 逻辑删除配置处理。
- Mapper 继承 `BaseMapper<Entity>`，只声明必要的自定义 SQL。
- Service 可继承 MyBatis-Plus service 能力，但业务方法必须表达领域语义。
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
- 事务方法放在 Spring Bean 的 public 方法上。
- 禁止同类内部自调用依赖事务生效。
- 事务内禁止调用慢外部接口。
- 事务内禁止执行不可控长循环。
- 捕获异常后仍需回滚时，必须重新抛出或显式标记 rollback。
- 只读查询可使用 `@Transactional(readOnly = true)`，但不作为性能优化万能手段。

## 10. 数据库与配置

- `schema.sql` 必须可重复执行。
- 初始化数据不得覆盖已有学习数据。
- H2 文件库路径依赖服务模块工作目录：`services/user-service` 下的 `../../data/bootapp` 指向 `boot-server/data/`。
- 移动模块前必须审计 H2 相对路径和文档链接。
- `boot-server/data/*.lock.db`、`*.trace.db` 禁止提交。
- `boot-server/data/bootapp.mv.db` 未经用户明确要求不得暂存。
- 密钥、密码、token 只从环境变量或安全配置注入，不写入代码和文档示例真实值。

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
