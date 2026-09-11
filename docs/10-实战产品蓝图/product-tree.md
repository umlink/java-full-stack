```text
boot-server/
├── pom.xml 【Maven 聚合父工程】
│   ├── 聚合 common-core 与 user-service 两个模块
│   └── 技术：Maven Reactor、Spring Boot BOM、Java 25 编译目标
│
├── infrastructure/
│   └── common-core/ 【✅ 已实现：纯 Java 公共契约】
│       └── src/main/java/com/example/bootserver/common/
│           ├── result/Result.java 【统一响应体】
│           │   └── 所有接口固定返回 code、message、data；HTTP 状态码表达通信结果，
│           │       业务 code 表达客户端可识别的失败类型。
│           └── error/ErrorCode.java、BusinessException.java 【错误分类与业务异常】
│               └── 集中维护 40000、40100、40300 等稳定错误协议；业务层抛出异常，
│                   Web 层决定如何转换为 HTTP 响应。
│
└── services/
    └── user-service/ 【✅ 已实现：当前模块化单体中的用户、认证与 RBAC 服务】
        ├── BootServerApplication.java 【应用启动入口】
        │   └── 技术：@SpringBootApplication、组件扫描、自动配置、Spring IoC。
        │
        ├── controller/ 【✅ HTTP 接口适配层：只处理请求、响应与参数校验】
        │   ├── AuthController.java 【注册、登录】
        │   │   ├── POST /auth/register
        │   │   │   └── 只接收 username、email、password；DTO 白名单让 role 等额外 JSON 字段
        │   │   │       不会进入业务层，客户端不能在注册时伪造 ADMIN。
        │   │   ├── POST /auth/login
        │   │   │   └── Controller 只串联账号认证与 JWT 签发，不直接查询数据库、比对密码
        │   │   │       或组装令牌。
        │   │   └── 技术：@RestController、@RequestBody、@Valid、DTO、构造器注入、OpenAPI。
        │   │
        │   ├── UserController.java 【当前主体与后台用户管理】
        │   │   ├── GET /users/me
        │   │   │   └── 从 SecurityContext 读取已认证用户 ID，而不信任前端传入的 userId。
        │   │   ├── GET /users、/users/page、/users/by-name、/users/{id}
        │   │   │   └── 后台用户查询：稳定排序、MyBatis-Plus 分页、按展示名模糊查询。
        │   │   ├── POST /users、PUT /users/{id}、DELETE /users/{id}
        │   │   │   └── 创建、更新与逻辑删除；客户端不能写 id、status、deleted 等内部字段。
        │   │   └── 技术：REST、@PathVariable、@RequestParam、@ModelAttribute、
        │   │       @AuthenticationPrincipal、Page<T>、输入 DTO 与 Entity 隔离。
        │   │
        │   └── dto/ 【接口输入输出契约，不是数据库实体】
        │       ├── RegisterRequest / LoginRequest 【注册、登录请求】
        │       │   └── record 与 @NotBlank、@Email、@Size：在 Controller 方法执行前校验输入。
        │       ├── CreateUserRequest / UpdateUserRequest / UserPageRequest 【用户管理请求】
        │       │   └── 将可写字段限制在业务允许范围，避免数据库字段被客户端直接覆盖。
        │       └── LoginResponse 【登录响应】
        │           └── 只返回 accessToken 与 expiresAt，不暴露密钥或密码哈希。
        │
        ├── security/ 【✅ JWT 认证与 RBAC 授权：请求进入业务层前的安全关卡】
        │   ├── SecurityConfig.java 【安全过滤链与路由规则】
        │   │   ├── /auth/**、OpenAPI、H2 Console 公开；/users/me 需要有效 JWT；
        │   │   │   其余 /users/** 需要 user:manage 权限。
        │   │   ├── STATELESS：服务端不保存 HttpSession，每次请求携带 Bearer Token。
        │   │   └── 技术：SecurityFilterChain、路径授权、最小权限、CSRF 适用边界。
        │   │
        │   ├── JwtTokenService.java 【JWT 签发与验签】
        │   │   ├── 将用户 ID 写入标准 sub，写入 iat、exp，并用 HS256 签名。
        │   │   ├── JWT 不保存角色或权限；权限每次从数据库加载，撤权后无需等待旧 Token 过期。
        │   │   └── 技术：JJWT、Claims、HS256、Base64 配置密钥、Instant、Duration。
        │   │
        │   ├── JwtAuthenticationFilter.java 【Bearer Token 转为当前请求身份】
        │   │   ├── 从 Authorization 头取 Bearer Token，验签并解析用户 ID。
        │   │   ├── 再确认用户仍存在、启用且未逻辑删除，随后才写入 SecurityContext。
        │   │   ├── 停用或逻辑删除后，旧 JWT 即使未过期也统一返回 401/40100。
        │   │   └── 技术：OncePerRequestFilter、SecurityContextHolder、Authentication、
        │   │       认证与授权分离、JWT 主体实时状态校验。
        │   │
        │   ├── UserAuthorityService.java 【主体状态与当前权限】
        │   │   └── 有效主体才加载权限码；权限为空的普通用户仍是已认证用户，访问管理接口时
        │   │       得到 403，而不是被误判为 401。
        │   ├── RestAuthenticationEntryPoint.java 【未认证出口】
        │   │   └── 无身份、令牌失效、主体停用或删除时统一返回 401/40100，
        │   │       不向客户端泄露具体原因。
        │   ├── RestAccessDeniedHandler.java 【无权限出口】
        │   │   └── 身份有效但权限不足时返回 403/40300。
        │   └── RoleCodes.java / PermissionCodes.java / BearerAuthentication.java 【稳定安全契约】
        │       └── 角色码、权限码、Bearer 协议前缀集中管理，避免在多层散落字符串。
        │
        ├── service/ 【✅ 业务规则与事务边界】
        │   └── UserService.java 【注册、登录、本地管理员引导、用户 CRUD】
        │       ├── 注册：校验用户名/邮箱唯一，BCrypt 哈希密码，插入用户并绑定默认 USER 角色。
        │       ├── 事务：插入用户与绑定角色由 @Transactional 保证原子性，任一步失败均回滚。
        │       ├── 登录：用户不存在、密码错误、账号停用均使用相同未认证语义；
        │       │   用户不存在时也执行 BCrypt 比对，减少通过耗时枚举账号的风险。
        │       ├── 本地管理员：仅配置环境变量时幂等创建或补全 ADMIN 角色关联，
        │       │   不覆盖已有学习数据。
        │       └── 技术：@Service、ServiceImpl、@Transactional、BCrypt、唯一约束、
        │           业务异常、LambdaQueryWrapper。
        │
        ├── mapper/ 【✅ 数据访问层：只负责查询与写入，不放登录、注册等业务规则】
        │   ├── UserMapper.java / RoleMapper.java 【MyBatis-Plus 基础 CRUD 与专用查询】
        │   ├── UserRoleMapper.java 【用户—角色关联】
        │   │   └── 处理复合键关联的插入与统计。
        │   ├── UserAuthorityMapper.java 【有效用户与权限联表查询】
        │   │   ├── 直接查询用户 id、status、deleted，确认 JWT 主体仍然有效。
        │   │   └── 通过 用户—角色—权限 多表关联读取去重后的权限码。
        │   └── 技术：BaseMapper、@Select、#{ } 参数绑定、DISTINCT、联表查询、
        │       LambdaQueryWrapper、Page<T>。
        │
        ├── entity/ 【✅ Java 持久化对象与表映射】
        │   ├── User.java 【t_user】
        │   │   ├── 登录名、展示名、邮箱、密码哈希、账号状态、逻辑删除与审计时间。
        │   │   └── @JsonIgnore 不序列化密码哈希；@TableLogic 将删除转为 deleted 标记更新。
        │   ├── Role.java 【t_role】
        │   │   └── 以 ADMIN、USER 等稳定业务码表达角色，不依赖自增主键。
        │   ├── UserRole.java 【t_user_role】
        │   │   └── 用户和角色多对多关联，表使用 user_id、role_id 复合主键。
        │   └── 技术：@TableName、@TableId、IdType.AUTO、@TableLogic、Lombok、
        │       下划线转驼峰映射、逻辑删除、多对多建模。
        │
        ├── config/ 【✅ 框架装配、外部化配置与启动期初始化】
        │   ├── PasswordConfig.java 【PasswordEncoder Bean】
        │   │   └── 注册和登录共用 BCrypt，未来替换密码算法只改一个装配点。
        │   ├── JwtProperties.java / JwtConfig.java 【app.jwt 类型化配置】
        │   │   └── JWT 密钥和 TTL 从 yml 或环境变量绑定，不散落在业务代码中。
        │   ├── ServletPathProperties.java 【MVC 外部 API 路径】
        │   │   └── 当前前缀来自 spring.mvc.servlet.path；Controller 只声明内部资源路径。
        │   ├── MybatisPlusConfig.java 【分页插件】
        │   │   └── 注册 H2 方言的分页拦截器，避免手写 LIMIT 和页码拼接。
        │   ├── OpenApiConfig.java 【接口文档与 Bearer 方案】
        │   │   └── Swagger/OpenAPI 展示契约，真实鉴权仍由 Security 过滤器链执行。
        │   └── LocalAdminProperties.java / LocalAdminInitializer.java 【本地 ADMIN 引导】
        │       └── ApplicationRunner 在启动后按环境变量执行幂等引导，密码不写入日志。
        │
        ├── handler/ 【✅ 跨 Controller 的统一异常出口】
        │   └── GlobalExceptionHandler.java
        │       ├── 参数校验失败 → 400/40000；业务异常 → 受控 HTTP 状态和错误码；
        │       │   唯一键冲突 → 409/40900。
        │       ├── 未预期异常只记录服务端日志，响应不暴露 SQL、堆栈、类名或配置。
        │       └── 技术：@RestControllerAdvice、@ExceptionHandler、ResponseEntity、
        │           Bean Validation 异常、DuplicateKeyException、信息泄露防护。
        │
        ├── resources/ 【✅ 运行参数与可重复数据库初始化】
        │   ├── application.yml 【可部署配置】
        │   │   ├── 端口、/api 前缀、H2 数据源、JWT TTL、逻辑删除、SQL 日志。
        │   │   └── JWT_SECRET_BASE64、BOOT_ADMIN_USERNAME、BOOT_ADMIN_PASSWORD 等敏感值
        │   │       只由环境变量注入，仓库不保存真实密钥和密码。
        │   └── db/schema.sql 【幂等 DDL 与 RBAC 种子数据】
        │       ├── 已实现 t_user、t_role、t_permission、t_user_role、t_role_permission。
        │       ├── CREATE TABLE IF NOT EXISTS 与按业务键守卫的 INSERT，使脚本重跑不覆盖学习数据。
        │       └── 技术：H2、MySQL 兼容模式、DDL、唯一约束、外键、幂等种子数据、
        │           逻辑删除、RBAC 多对多模型。
        │
        ├── src/test/ 【🧪 自动化验收：把功能转成可重复验证的行为】
        │   ├── controller/AuthControllerWebTests.java 【MockMvc】
        │   │   └── 验证 JSON 绑定、DTO 白名单、字段校验、登录与统一错误响应。
        │   ├── controller/UserControllerWebTests.java 【MockMvc】
        │   │   └── 验证用户管理 Controller 的输入、输出与调用边界。
        │   ├── handler/GlobalExceptionHandlerWebTests.java 【异常响应】
        │   ├── service/RegisterFlowIntegrationTests.java 【注册流程】
        │   │   └── 验证真实 H2 数据库中的密码哈希、默认角色、事务与唯一约束行为。
        │   ├── service/LoginFlowIntegrationTests.java 【登录认证】
        │   ├── security/JwtAuthenticationIntegrationTests.java 【真实 HTTP 安全验收】
        │   │   └── 验证 USER 返回 403、ADMIN 可访问，以及用户停用或逻辑删除后旧 JWT
        │   │       访问 /users/me、/users 均返回 401/40100。
        │   ├── config/LocalAdminBootstrapIntegrationTests.java 【本地管理员幂等引导】
        │   ├── ApiPrefixIntegrationTests.java 【API 前缀、OpenAPI、Swagger UI】
        │   └── BootServerApplicationTests.java 【应用与数据库初始化】
        │
        └── 商品域 【⬜ 尚未实现：对应已解锁的 M2-00】
            ├── entity/Product.java / Sku.java 【尚未创建】
            ├── mapper/ProductMapper.java / SkuMapper.java 【尚未创建】
            └── db/schema.sql 的 t_product、t_sku 【尚未创建】
                └── 后续会学习：DECIMAL 与 BigDecimal、SKU 唯一编码、外键、乐观锁 version、
                    商品状态约束、幂等种子数据与数据库不变量测试。
```
