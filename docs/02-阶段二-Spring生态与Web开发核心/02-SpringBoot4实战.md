# 阶段二 · 小点 2：Spring Boot 4.x 实战

> 所属：阶段二 Spring 生态与 Web 开发核心
> 定位：Boot 的价值是「约定优于配置」——**理解自动装配后，它就从「魔法」变成「可以推理的系统」**：为什么引一个 starter 就有了数据源？为什么改个 yml 前缀就能连上别的 Redis？答案都在本讲。
> 版本基线：Boot 4 = Framework 7 = Jakarta EE 11 基线；starter 模块化细分、引入 JSpecify 空安全标注等——具体升级差异以官方迁移指南为准。

## 快速入门

> 本节为「Boot 速览」：先认识 Boot 怎么让一个项目跑起来、`application.yml` 配什么；「自动装配原理、starter 机制」留在正文提高部分。

### 本讲核心关键词速查

| 关键词 | 一句话大白话 | 例子 |
|-|-|-|
| Spring Boot | 让 Spring 项目「开箱即用」的框架 | 引依赖 + 写几个类就能出接口 |
| starter | 一个依赖包，引进来就自带一堆能力 | `spring-boot-starter-web` |
| @SpringBootApplication | 项目入口：自动配置+组件扫描+配置类 | 主类上标注 |
| 自动装配 | Spring 看你要什么，自动建好 Bean | 引了 web 就有 Tomcat + 一堆自动配置 |
| application.yml | 项目配置文件 | 端口/数据库/日志写这里 |
| @ConfigurationProperties | 把配置映射成强类型 Bean | 配置文件 → Java 类 |
| Actuator | Boot 自带的监控端点 | `/actuator/health` |

### 本讲在解决什么问题

- **问题**：以前搭 Spring 要配一堆 XML/类，很繁琐。Boot 用「约定优于配置」——按套路放文件、引 starter，项目就能跑。
- **你要带走的一句话**：`@SpringBootApplication` 是入口，它同时干了「自动配置 + 组件扫描 + 自己是个配置类」三件事；写配置进 `application.yml`，很多能力靠「引 starter」就有了。

### 最简可运行示例（照抄能跑）

```java
// ① 主类: 加上 @SpringBootApplication 就是启动入口
@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);   // 启动内嵌 Tomcat
    }
}
```

```yaml
# ② application.yml: 项目配置放在这里
server:
  port: 8080                 # 服务端口
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/demo   # 数据库地址
    username: root
    password: ${DB_PASSWORD}     # 密钥用环境变量, 别写死
```

```java
// ③ 一个接口: 引了 spring-boot-starter-web 就能用 @RestController
@RestController
public class HelloController {
    @GetMapping("/hello")
    public String hello() { return "你好"; }
}
```

> 代码备注（逐行解释）：
> - `@SpringBootApplication`：让 Spring 自动配置、扫描组件、并把自己当配置类——一个注解搞定三件事。
> - `SpringApplication.run(...)`：启动内嵌 Tomcat 并运行业务。
> - `application.yml`：集中放端口、数据库等配置；`${DB_PASSWORD}` 从环境变量读密钥（不写死）。
> - `@RestController` + `@GetMapping`：引了 `spring-boot-starter-web` 后就有了 web 能力，写个类就能出接口。
> - 你只引了一个 starter、写了几行，项目就跑起来了——这就是 Boot「约定优于配置」。

### 关键概念 / 注解说明

| 概念 / 注解 | 干什么 | 最易踩的坑 |
|-|-|-|
| `@SpringBootApplication` | 启动入口（自动配置+扫描+配置类） | 每个项目只有一个；要放在能扫到所有包的顶层 |
| starter | 依赖包，引进即生效 | 别乱引多余 starter（会让自动配置打架） |
| `application.yml` | 配置文件 | 多环境用 `spring.profiles.active` 切换 |
| `@ConfigurationProperties` | 配置映射成强类型对象 | 前缀要跟 yml 对上 |
| `@Value` | 读单个配置值 | 多个相关配置用 `@ConfigurationProperties` 更好 |
| Actuator | 监控端点 | 记得加访问控制（阶段二第 6 讲），别裸奔公网 |

### 常用约定 / 命名提示

- **端口默认 8080**；改配置进 `application.yml` 的 `server.port`。
- **数据库密钥别写死**：用 `${ENV}` 从环境变量读，是这套文档反复强调的红线。
- **学了 `@ConfigurationProperties` 就用它**：别到处 `@Value` 散落配置，把一组配置收敛成强类型对象更清晰。
- **Actuator 端点要保护**：`/actuator/**` 一旦暴露公网，等于把配置/线程/堆交出去——记得加权限。

## 精简大纲

1. 自动装配原理：@SpringBootApplication 拆解与 starter 机制
2. 配置体系：application.yml / 多环境 / @ConfigurationProperties
3. 内置容器与 Filter / Interceptor 执行顺序
4. Actuator 监控
5. AOT 与原生镜像
6. 日志体系与 MDC TraceID

## 学习内容详情

### 1. 自动装配原理

#### 1.1 @SpringBootApplication 拆解

```java
@SpringBootApplication        // 这个注解 = 下面三个的组合
//  @SpringBootConfiguration   → 本质是 @Configuration：这个类是配置类（容器入口）
//  @ComponentScan             → 扫描本包及子包的 @Component（隐式递归, 范围=主类所在包）
//  @EnableAutoConfiguration   → 开启自动装配：把 classpath 里"认得"的组件自动配好
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

> **starter**：一组依赖 + 一份自动配置的「套餐」。`spring-boot-starter-web` 引入即得 Tomcat + Spring MVC + Jackson——「引了就有」不是依赖本身有魔力，而是它的 jar 里带着自动配置类。

#### 1.2 条件装配：自动配置的开关

```java
// 看一个自动配置类的骨架（简化自 spring-boot-autoconfigure 的真实源码）
@AutoConfiguration                          // Boot 3+ 替代旧 @Configuration 的语义化注解
@ConditionalOnClass(DataSource.class)       // 开关①: classpath 里有 DataSource 这个类才生效
                                              // （没引 JDBC 依赖时这份配置整体跳过）
@ConditionalOnMissingBean(DataSource.class) // 开关②: 容器里还没有用户自己配的 DataSource
                                              // （用户手动配了就尊让用户 —— "约定让位于显式"）
@ConditionalOnProperty(
        prefix = "app.datasource",          // 开关③: 配置开关 app.datasource.enabled=true
        name = "enabled", havingValue = "true")
public class DataSourceAutoConfiguration {

    @Bean
    @ConfigurationProperties("app.datasource")  // 把 app.datasource.* 的 yml 批量绑到属性
    DataSource dataSource(DataSourceProperties props) {
        return props.initializeDataSourceBuilder().build();
    }
}
```

- 装配清单登记文件：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（Boot 2.7 前是 `spring.factories`——旧资料常见，注意区分）。
- 排查工具：启动参数 `--debug` 会打印**条件评估报告**（哪个配置为什么没生效，`CONDITIONS EVALUATION REPORT`）。

#### 1.3 写一个自定义 starter（步骤）

```
my-spring-boot-starter/
├── pom.xml                          （只依赖 spring-boot-autoconfigure）
└── src/main/resources/META-INF/spring/
    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
        → 内容一行: com.demo.starter.WarnAutoConfiguration   （登记自动配置类的全限定名）
```

```java
@AutoConfiguration
@ConditionalOnClass(WarnClient.class)                 // 用户引了你的核心包才装配
@EnableConfigurationProperties(WarnProperties.class)  // 启用类型安全配置绑定
public class WarnAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean                         // 用户自定义的 WarnClient 优先
    WarnClient warnClient(WarnProperties props) {
        return new WarnClient(props.getWebhook());    // 用 yml 里的配置构建客户端
    }
}
```

### 2. 配置体系

#### 2.1 多环境 profile

```yaml
# application.yml —— 公共配置
spring:
  application:
    name: order-service
  threads:
    virtual:
      enabled: true        # Java 21+ 虚拟线程一键启用（WebMVC 每请求一个虚拟线程）

---
spring:
  config:
    activate:
      on-profile: dev     # dev profile：本地开发的覆盖段
price:
  api-key: dev-key-123

---
spring:
  config:
    activate:
      on-profile: prod    # prod profile：生产的覆盖段（敏感值实际来自环境变量, 见下）
price:
  api-key: ${PRICE_API_KEY}   # 占位符引用环境变量 → 密钥不进代码库
```

启动激活：`java -jar app.jar --spring.profiles.active=prod` 或环境变量 `SPRING_PROFILES_ACTIVE=prod`。

#### 2.2 类型安全绑定 @ConfigurationProperties

```java
// ✅ 推荐：一个前缀一个类, 字段类型即文档, 编译期就能发现拼写错
// 松散绑定: yml 的 remote-host 自动映射到 remoteHost
@ConfigurationProperties(prefix = "price")
public record PriceProperties(
        String apiKey,             // price.api-key
        Duration timeout,          // price.timeout: 3s → 自动转 Duration！
        List<String> endpoints     // price.endpoints[0], [1]...
) {}

// 再配上 JSR-303 校验, 配置缺失/非法直接启动报错（fail-fast 优于运行时 NPE）
// @Validated + @NotBlank String apiKey 即可
```

> **外部化配置优先级**（高 → 低）：命令行参数 → 环境变量 → profile 段 → `application.yml`。生产纪律：**密码 / Key 走环境变量，不落文件**；同一份镜像靠环境变量区分环境（阶段五 CI/CD「一次构建处处部署」的基础）。

### 3. 内置容器与三层拦截

```java
@Component
public class TraceFilter extends OncePerRequestFilter {   // Filter：Servlet 规范层
    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws IOException, ServletException {
        res.setHeader("X-Trace", traceId());   // 最早执行：连请求体还没被解析
        chain.doFilter(req, res);              // 放行到下一层（Interceptor → Controller）
    }
}

@Component
public class AuthInterceptor implements HandlerInterceptor {  // Interceptor：Spring MVC 层
    @Override
    public boolean preHandle(HttpServletRequest req,
                             HttpServletResponse res, Object handler) {
        // 能拿到 handler（即将执行的方法）→ 适合"按方法/路由"做鉴权与限流
        return checkToken(req);                // false = 中断, 不进 Controller
    }
    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res,
                                Object handler, Exception ex) {
        // 视图渲染完成后（异常也已处理）→ 适合清理 ThreadLocal
    }
}
```

> **执行顺序**：`Filter(doFilter 前) → Interceptor(preHandle) → AOP(方法切面) → Controller → AOP → Interceptor(postHandle/afterCompletion) → Filter(后)`。
> 注意：静态资源、错误分发（ERROR dispatch）等非 Controller 请求可能不经过完整链路，排查问题时先确认请求类型。
> 选型口诀：**跨技术栈的进 Filter（如 TraceID / CORS），跟路由方法绑定的进 Interceptor（如权限），跟 Bean 方法绑定的用 AOP（如审计日志）**。容器可换 Jetty / Undertow：排除 `spring-boot-starter-tomcat` 引入替代 starter 即可。

### 4. Actuator 监控

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus   # 默认只开 health/info, 生产按需暴露
  endpoint:
    health:
      show-details: when-authorized               # 健康详情别裸奔给匿名
```

```bash
curl localhost:8080/actuator/health
# > {"status":"UP","components":{"db":{"status":"UP"},"redis":{"status":"UP"}}}

curl localhost:8080/actuator/metrics/http.server.requests
# > {"name":"http.server.requests","measurements":[
# >   {"statistic":"COUNT","value":1203},
# >   {"statistic":"MAX","value":"0.412s"}]}     ← 这些数据就是 K8s 探针和 Prometheus 的数据源
```

自定义 Endpoint：`@Endpoint(id = "warmup")` + `@ReadOperation` 方法。

### 5. AOT 与原生镜像

| 方案 | 启动 | 代价 |
|-|-|-|
| 传统 JIT | 数秒 | — |
| **Project Leyden AOT 缓存**（JDK 24 交付 / 25 可用） | 启动提速约 40%+（因应用而异） | 构建期预生成代码缓存，无封闭世界假设 |
| GraalVM Native Image | 毫秒级、内存减半 | 构建慢；**封闭世界**：反射 / 动态代理需显式配置 |

> **Leyden AOT 定位**：前瞻技术，了解即可；当前真正可落地的是 GraalVM Native Image，且只在 Serverless / 弹性伸缩敏感场景值得。

选型判断：Serverless / 弹性伸缩敏感（冷启动致命）才上 Native Image；常规长驻服务 Leyden AOT 或不动。

### 6. 日志体系与 MDC TraceID

- **SLF4J**（门面，只定义 API）+ **Logback**（Boot 默认实现）——门面模式让实现可替换，业务代码只 `import org.slf4j.*`。
- **MDC**（Mapped Diagnostic Context）：日志框架的 ThreadLocal 上下文——塞进去的值会自动出现在该线程打出的每条日志里。

```java
@Component
public class TraceIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        // 优先沿用上游传入（网关/调用方）, 否则自己生成 —— 保证链路不断
        String traceId = Optional.ofNullable(req.getHeader("X-Trace-Id"))
                                 .orElseGet(() -> UUID.randomUUID().toString().substring(0, 12));
        MDC.put("traceId", traceId);            // 进 MDC: 本线程后续所有日志自动携带
        res.setHeader("X-Trace-Id", traceId);   // 回写响应头, 前端报障时能提供线索
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.clear();        // ⚠️ 线程会被复用（线程池）, 不清就"串号"到别的请求
        }
    }
}
```

```xml
<!-- logback-spring.xml 的 pattern 里加 %X{traceId} -->
<pattern>%d{HH:mm:ss} %-5level [%X{traceId}] %logger{20} - %msg%n</pattern>
```

> 输出效果：`14:02:11 INFO [a3f8c2e1d09b] o.s.d.OrderService - 下单成功 orderId=1001`
> 级别语义纪律：ERROR = 需要人介入；WARN = 能自愈但要关注；INFO = 关键路径节点；DEBUG = 开发期细节。**敏感信息（手机号 / Token / 身份证）脱敏后再打**。

### 坑点提醒

- **`@Value` 与 `@ConfigurationProperties` 别混着用**：前者散落各处、无类型校验；统一后者，一个前缀一个 record。
- yml 同名 key 覆盖顺序看 profile 激活态，**公共配置别写进 profile 段**（否则关掉 profile 配置就丢了）。
- `management.endpoints.web.exposure.include: "*"` 在生产等于裸奔——`/actuator/env` 会吐出所有配置（含密码）。
- MDC 不 `clear()` 是线上「日志串请求」的经典原因——尤其线程池 / 虚拟线程复用场景。
- 启动报「没有某个 Bean」时，先 `--debug` 看条件报告，再查扫描范围，不要盲目加注解。

## 本节自检

- [ ] 能拆解 `@SpringBootApplication` 三个注解各自的作用
- [ ] 自动装配是怎么发生的？写一个自定义 starter 需要哪几步？
- [ ] 能说出外部化配置的优先级顺序，以及生产环境敏感配置的推荐位置
- [ ] 能说清 Filter / Interceptor / AOP 三层的执行顺序与典型分工
- [ ] 能描述 MDC + TraceID 的日志方案，以及 ERROR / WARN / INFO 的级别语义

## 本节配套思考题

1. 引入 `spring-boot-starter-data-redis` 但没配 host，应用为什么能启动、什么时候才报错？这个行为和自动装配的哪个特性有关？
2. 同一个配置在 yml、环境变量、命令行各写了一份，最终生效哪个？用这条规则解释「K8s 里改环境变量就能切配置」。
3. 虚拟线程开启后，`synchronized` 里做阻塞 IO 的老代码会发生什么（联系阶段一第 7 讲 pinning）？
