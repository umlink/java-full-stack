# boot-server：与学习文档配套的 Spring Boot 4 多模块工程

本工程是 [实战产品蓝图](../docs/10-实战产品蓝图/README.md)（BootMall「云市商城」）的**实现载体**：每学完一个阶段，按蓝图的「业务场景 × 技术方案」映射表往这里落一块功能。当前进度：**用户 CRUD、注册与 JWT 登录已就位**，下一步是 Bearer token 认证过滤器（见蓝图的 [M1 实践队列](../docs/10-实战产品蓝图/04-里程碑与当前计划.md)）。

配套客户端位于仓库根目录：`admin-client/` 对应 B 端管理后台，`user-client/` 对应 C 端商城。两者均已初始化为独立 React + TypeScript + Vite 工程，但尚未接入本服务；B 端在 M1 认证与授权验收后接入，C 端在 M2 交易核心验收后接入。

仓库 [docs/](../docs/README.md) 里学习路线（阶段零 → 阶段八）的**实操落点**：
文档教概念，这个工程给你「跑得起来的最小样例」。当前版本覆盖阶段零/阶段二的基础知识。

> 💡 **数据库选择说明**：初期用 **H2 嵌入式数据库**是为了**前期学习方便**——零安装、clone 即跑、数据文件随仓库走。后期学习到阶段三（MySQL / ORM）时，按文末「学习扩展点」切换到 MySQL 即可（引驱动 + 改一处 datasource url，H2 的 `MODE=MySQL` 已提前对齐 MySQL 方言，切换成本最低）。

## 技术栈与版本

| 组件 | 版本 | 对应文档 |
|-|-|-|
| Java | 25（LTS） | 阶段零 02 讲（环境基线） |
| Spring Boot | 4.0.0 | 阶段二 02 讲（BOM 示例同款） |
| MyBatis-Plus | 3.5.17（Boot 4 专用 starter + jsqlparser） | 阶段三第 2 讲（ORM） |
| H2 | BOM 托管（嵌入式，免安装） | 阶段二 05 讲（「仿制数据库」） |
| JJWT | 0.13.0 | M1-06（HS256 登录令牌） |
| Maven | 3.9.16（**Maven Wrapper 自带，无需安装**） | 阶段二 02 讲（聚合工程范式） |

## 模块结构

```
boot-server/
├── pom.xml                          # 聚合父 pom：锁版本（BOM 范式）+ 声明模块
├── infrastructure/                  # 公共底座（打包为普通 jar，被业务服务引用）
│   └── common-core/                 # 统一响应体 Result 等通用组件（纯 Java，零框架依赖）
└── services/                        # 业务服务（每个都是可独立部署的 Spring Boot 应用）
    └── user-service/                # 用户服务（后续 order-service 等平级并列）
        ├── pom.xml
        └── src/main/
            ├── java/com/example/bootserver/
            │   ├── BootServerApplication.java # 入口（@SpringBootApplication）
            │   ├── controller/UserController # REST CRUD（统一响应体）
            │   ├── service/UserService       # 业务层（MP ServiceImpl）
            │   ├── mapper/UserMapper         # 数据层（MP BaseMapper）
            │   ├── entity/User               # 实体（@TableName + @TableLogic）
            │   └── config/MybatisPlusConfig  # 分页插件（H2 方言）
            └── resources/
                ├── application.yml           # 配置：H2 文件库 + 逻辑删除 + SQL 打印
                └── db/schema.sql             # 建表 + 初始数据（随仓库提交，启动自动执行）
```

> 布局说明：`infrastructure/`（技术底座）与 `services/`（业务服务）分层，是微服务工程化的标准形态——底座模块可被多个服务复用，服务之间相互独立、可单独构建部署。当前只有一个 user-service，后续加服务（如 order-service）平级放在 `services/` 下即可。

## 快速开始

**环境要求**：JDK 25 与一个运行时 JWT 密钥。本仓库自带 **Maven Wrapper**（`mvnw` / `mvnw.cmd`），不需要安装 Maven。启动前生成一次本机密钥（不提交）：

```bash
export JWT_SECRET_BASE64="$(openssl rand -base64 32)"
```

密钥通过环境变量注入，缺失时应用会在启动阶段拒绝运行；测试使用独立假密钥，不依赖本机环境变量。

**方式一：IDEA（推荐）**
1. `File → Open` 选择 `boot-server/pom.xml`，以 Maven 工程打开（IDEA 自动识别 wrapper 与 JDK）
2. 首次打开等待依赖下载（国内网络慢可先配阿里云镜像，见[阶段零 02 讲](../docs/00-阶段零-起点盘点与补课/02-工程环境清单.md)）
3. 直接运行 `BootServerApplication` 的 main 方法

**方式二：命令行（无需安装 Maven）**

Windows 用 `mvnw.cmd`，macOS / Linux 用 `./mvnw`（下面以 Windows 为例）：

```bash
cd boot-server
# ① 构建并安装全部模块（首次 / 改动了 pom 后执行）
mvnw.cmd -pl services/user-service -am install -DskipTests
# ② 启动应用（注意：不要带 -am，run 只对服务模块执行）
mvnw.cmd -pl services/user-service spring-boot:run
```

> 小坑备忘：`spring-boot:run` 带 `-am` 会把启动目标也跑到聚合父 pom 上（父模块没有 main 类会报错），所以启动单独一条命令。已装全局 Maven 3.9+ 的机器直接用 `mvn` 替换 `mvnw.cmd` 同样可行。

启动成功后控制台会打印 SQL（学习期特意打开），访问：

- 接口：`http://localhost:8080/api/users`
- H2 控制台：`http://localhost:8080/h2-console`（JDBC URL 填 `jdbc:h2:file:../../data/bootapp;MODE=MySQL`，用户 `sa`，密码留空——与 datasource 一致，相对服务模块工作目录）
- 数据库文件：`boot-server/data/`（随工程走，可提交记录——学习期每次实验的库状态都留档，方便回看 diff 与回滚；**首次启动自动建表灌初始数据，data/ 已有数据则保留历史**）

## 接口一览

**统一接口前缀**：`spring.mvc.servlet.path` 在 `application.yml` 中配置为 `/api`。Controller 仅声明资源路径（例如 `/users`），因此外部接口为 `/api/users`；后期调整为 `/api/v1` 时只修改该配置，不逐个修改 Controller。H2 Console 保持 `http://localhost:8080/h2-console`，不受 MVC 前缀影响。

| 方法 | 路径 | 说明 |
|-|-|-|
| GET | `/api/users` | 列表（自动过滤已逻辑删除） |
| GET | `/api/users/page?page=1&size=10` | 分页 |
| GET | `/api/users/by-name?name=xx` | 名称模糊查询 |
| GET | `/api/users/{id}` | 详情（不存在返回 40400） |
| POST | `/api/users` | 新增（body 传 JSON） |
| PUT | `/api/users/{id}` | 更新 |
| DELETE | `/api/users/{id}` | 逻辑删除（UPDATE deleted=1） |
| POST | `/api/auth/register` | 注册用户并默认绑定 USER 角色 |
| POST | `/api/auth/login` | 校验用户名和密码，返回短期 JWT |

## 接口验证（curl）

```bash
# 列表（首次启动返回 3 条初始数据；data/ 已有历史则返回当前数据）
curl http://localhost:8080/api/users

# 分页
curl "http://localhost:8080/api/users/page?page=1&size=2"

# 按名称模糊查询
curl "http://localhost:8080/api/users/by-name?name=Al"

# 新增
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Dave","email":"dave@example.com","age":32}'

# 更新
curl -X PUT http://localhost:8080/api/users/1 \
  -H "Content-Type: application/json" \
  -d '{"age":26}'

# 删除（逻辑删除：实际执行 UPDATE ... SET deleted=1）
curl -X DELETE http://localhost:8080/api/users/1

# 详情（删除后查返回 40400「用户不存在」）
curl http://localhost:8080/api/users/1

# 注册并登录（accessToken 是下一张 JWT 认证过滤器卡片要消费的 Bearer token）
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"dave","email":"dave@example.com","password":"secret123"}'
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"dave","password":"secret123"}'
```

## 与文档的对应关系（学到这里回来对照）

| 工程里的东西 | 文档 |
|-|-|
| `Result` 统一响应体 / RESTful 风格 | 阶段二 03 讲「统一响应体与 API 设计」 |
| `@SpringBootApplication` / 多模块聚合 | 阶段二 02 讲「Boot 实战 / 聚合工程」 |
| `@Service` Bean / 构造器注入 / 自动装配 | 阶段二 01 讲 + 阶段零 05 讲「Bean 是什么」 |
| Mapper 接口无实现类却能查库 | 阶段二 01 讲「框架魔法解密」（动态代理） |
| H2（「仿制数据库」） | 阶段二 05 讲测试工程 |
| 逻辑删除字段 deleted | 阿里规约（阶段零 02 讲）+ MyBatis-Plus 全局配置 |
| 分页插件（jsqlparser） | 阶段三（ORM 数据访问）预告 |

## 学习扩展点（自己动手）

- 把 `application.yml` 的 `log-impl` 换成 `org.apache.ibatis.logging.slf4j.Slf4jImpl`，感受日志门面（阶段二 02 讲 SLF4J）
- 换 MySQL：引入 `mysql-connector-j` 依赖 + 改 datasource URL，体验「H2 和 MySQL 只差一处配置」
- 给 UserService 加一个带 `@Transactional` 的方法，然后故意抛异常看回滚（阶段二 01 讲）
