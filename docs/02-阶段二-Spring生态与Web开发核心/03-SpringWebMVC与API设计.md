# 阶段二 · 小点 3：Spring Web MVC 与 API 设计

> 所属：阶段二 Spring 生态与 Web 开发核心
> 定位：Web 层是业务开发的日常主场。除框架用法外，本讲把「API 设计规范」（统一响应 / 错误码 / 幂等 / 分页）作为工程素养重点展开——**这是从「会写接口」到「接口可维护」的分水岭**：前端同事对接你的接口时是舒服还是骂人，取决于这一讲。

## 快速入门

> 本节为「写接口速览」：先认识怎么接参数、返回数据、统一响应；「幂等、分页、错误码规范」留在正文提高部分。

### 本讲核心关键词速查

| 关键词 | 一句话大白话 | 例子 |
|-|-|-|
| Controller | 处理 HTTP 请求的控制器 | `@RestController` |
| @GetMapping / @PostMapping | 接 GET / POST 请求 | 对应路由方法 |
| @RequestParam | 取 URL 查询参数 | `?name=xx` |
| @RequestBody | 取请求体 JSON | POST 传的对象 |
| 统一响应 | 所有接口返回一种格式 | `{code, message, data}` |
| 全局异常 | 一处处理所有报错 | `@RestControllerAdvice` |
| 幂等 | 同一请求执行多次不影响结果 | 防止重复支付/下单 |
| 分页 | 数据分批返回 | `?page=1&size=20` |

### 本讲在解决什么问题

- **问题**：写接口不难，难的是「接口可维护、前端对接舒服」。要统一响应格式、统一错误码、处理参数校验、分页、幂等。
- **你要带走的一句话**：好的接口 = **统一响应 + 明确错误码 + 参数校验 + 合理分页 + 幂等**。前端同事对接时舒服，是因为这些约定你在一开始就定好了。

### 最简可运行示例（照抄能跑）

```java
@RestController                        // 处理 HTTP 请求并返回 JSON
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/{id}")               // GET /api/users/123
    public ApiResponse<User> get(@PathVariable Long id) {     // @PathVariable: 取路径里的 id
        return ApiResponse.ok(userService.findById(id));
    }

    @PostMapping                       // POST /api/users
    public ApiResponse<Long> create(@RequestBody CreateUserReq req) {   // @RequestBody: 取 JSON 请求体
        return ApiResponse.ok(userService.create(req));
    }
}
```

> 代码备注（逐行解释）：
> - `@RestController`：这个类处理 HTTP 请求，返回值自动转 JSON。
> - `@RequestMapping("/api/users")`：类上统一加路径前缀。
> - `@PathVariable Long id`：从 URL 路径 `{id}` 里拿值（`/api/users/123` 的 123）。
> - `@RequestBody CreateUserReq req`：从 HTTP 请求体（JSON）反序列化成 Java 对象。
> - `ApiResponse.ok(...)`：统一响应包装——所有接口都返回 `{code, message, data}`，前端好写统一处理。

### 关键注解 / 概念说明

| 注解 / 概念 | 干什么 | 最易踩的坑 |
|-|-|-|
| `@PathVariable` | 取 URL 路径参数 | 路径模板里要写 `{id}` |
| `@RequestParam` | 取查询参数 | `@RequestParam(required=false)` 可选 |
| `@RequestBody` | 取 JSON 请求体 | 没这注解就拿不到 body |
| `@RestControllerAdvice` | 全局异常统一处理 | 别漏，否则异常直接裸奔 |
| `@Valid` / `@Validated` | 参数校验 | 校验注解（`@NotBlank` 等）要配 `@Valid` |
| 统一响应 `ApiResponse` | 打包返回格式 | 错误时 HTTP 状态码与 code 要对应 |

### 常用约定 / 命名提示

- **统一响应体**：`{code, message, data}`，成功 code=0，业务失败可回 HTTP 200 + 业务 code，系统错误回对应状态码。
- **幂等的三板斧**：防重 token / 唯一索引 / 状态机——用于支付、下单这种不能重复执行的场景。
- **分页约定**：页码式用 `page/size/total`，游标式用 `cursor/nextCursor`——按业务选。
- **错误码分段**：如 `4xxxx` 参数、`5xxxx` 业务、`9xxxx` 系统，前端按段决定提示方式。

## 精简大纲

1. Spring Web MVC 核心：RESTful / 参数绑定 / 全局异常 / 校验
2. API 设计规范：统一响应体 / 错误码 / 版本化 / 幂等 / 分页
3. 接口文档：SpringDoc / OpenAPI 3.0
4. 文件上传与 CORS

## 学习内容详情

### 1. Web MVC 核心

#### 1.1 参数绑定与转换

```java
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    // @PathVariable：路径变量（RESTful 资源定位）
    @GetMapping("/{id}")
    public Order detail(@PathVariable Long id) { return orderService.find(id); }

    // @RequestParam：查询参数（GET /list?status=PAID&page=2）
    @GetMapping
    public Page<Order> list(@RequestParam String status,
                            @RequestParam(defaultValue = "1") int page) { ... }

    // @RequestBody：JSON 请求体 → DTO（POST/PUT 用）
    @PostMapping
    public ApiResponse<Long> create(@RequestBody @Valid CreateOrderCmd cmd) { ... }

    // @RequestHeader：取头（如网关透传的用户身份）
    @GetMapping("/mine")
    public List<Order> mine(@RequestHeader("X-User-Id") Long userId) { ... }
}
```

> 对照 NestJS：`@Param()` ↔ `@PathVariable`、`@Query()` ↔ `@RequestParam`、`@Body()` ↔ `@RequestBody`——一一对应，换个注解包名而已。

#### 1.2 DTO 与校验（校验上移到入参对象）

```java
// record 做 DTO（不可变 + 自动构造器/equals）：字段上的注解就是契约
public record CreateOrderCmd(
        @NotNull(message = "商品 id 不能为空")          // Jakarta Bean Validation 标准注解
        Long productId,

        @Min(value = 1, message = "数量至少为 1")
        @Max(value = 99, message = "单笔最多 99 件")
        Integer quantity,

        @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不合法")
        String phone
) {}
// Controller 入参加 @Valid 即触发校验；失败自动 422/400, 不进业务代码（对照 NestJS 的 Pipe + class-validator）
```

#### 1.3 全局异常：@RestControllerAdvice

```java
// 统一异常处理：业务代码只管"抛", 转 HTTP 响应这件事全收口在这里
// （对照 NestJS 的 ExceptionFilter —— 概念完全一致）
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)          // 业务异常：带错误码, 转 4xx/5xx 由异常自己声明
    public ResponseEntity<ApiResponse<Void>> biz(BizException e) {
        return ResponseEntity.status(e.getHttpStatus())
                .body(ApiResponse.fail(e.getBizCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)   // @Valid 校验失败
    public ResponseEntity<ApiResponse<Void>> invalid(MethodArgumentNotValidException e) {
        List<String> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();                                     // 收齐全部字段错误 —— 只报第一个, 前端要修一版错一版
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(BizCode.PARAM_INVALID, String.join("; ", errors)));
    }
}
```

> Spring 6+ 另有 RFC 7807 标准的 `ProblemDetail` 可用；但国内企业实践多为自定义统一响应体（下节），二选一、全公司统一即可。

### 2. API 设计规范（工程素养）

#### 2.1 统一响应体与错误码

```java
// 所有接口只有一种成功结构 {code, message, data} —— 前端只写一套判断逻辑
public record ApiResponse<T>(int code, String message, T data) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);          // 0 = 成功
    }
    public static <T> ApiResponse<T> fail(BizCode c, String msg) {
        return new ApiResponse<>(c.getCode(), msg, null);
    }
}

// 错误码分段规划：一看区间就知道该找谁
// 每个业务码同时声明对应 HTTP 状态码 —— 业务码与 HTTP 状态码的映射固化在枚举里, 异常处理器统一转换
public enum BizCode {
    PARAM_INVALID(40001, HttpStatus.BAD_REQUEST),          // 4xxxx: 调用方的问题（参数/鉴权/频率）
    ORDER_NOT_FOUND(40401, HttpStatus.NOT_FOUND),
    ORDER_STATUS_INVALID(50001, HttpStatus.CONFLICT),      // 5xxxx: 业务规则拒绝
    STOCK_NOT_ENOUGH(50002, HttpStatus.CONFLICT),
    SYSTEM_ERROR(90000, HttpStatus.INTERNAL_SERVER_ERROR); // 9xxxx: 系统级, 值得告警
    private final int code;
    private final HttpStatus httpStatus;
    BizCode(int code, HttpStatus httpStatus) { this.code = code; this.httpStatus = httpStatus; }
    public int getCode() { return code; }
    public HttpStatus getHttpStatus() { return httpStatus; }
}
```

> 响应示例：`{"code": 50002, "message": "库存不足", "data": null, "traceId": "a3f8c2e1"}`——`traceId` 联动上一讲的 MDC，前端报障时带上它，日志一查一个准。

#### 2.2 版本化策略

- **URL 路径版本**（`/api/v1/orders`，推荐中小团队）：直观、网关路由友好、curl 可复现；代价是 URL 里「版本」不符合纯 REST 纯洁癖——工程上不纠结这个。
- Header 版本（`Accept: application/vnd.demo.v2+json`）：无 URL 污染但调试麻烦。
- 原则：**破坏性变更必须升版本**（v1 继续跑），非破坏性（加可选字段）不升。

#### 2.3 幂等设计（重试安全的基础）

**幂等（Idempotency）**：同一个请求发一次和发 N 次，效果相同。HTTP 重试（超时重试、用户狂点）天然存在，所以「写接口」必须幂等——这也是阶段四 RPC 重试的配套纪律。

```java
@Service
public class IdempotentOrderService {
    private final StringRedisTemplate redis;
    private final OrderRepository orderRepo;

    // 方案一：防重 token —— 客户端先领 token, 提交时带上, 服务端原子消费
    public Long placeOrder(String idempotencyKey, CreateOrderCmd cmd) {
        // SETNX: 不存在才写入成功 = 抢到"执行权"; 30s 过期兜底客户端不再回传的死 token
        Boolean first = redis.opsForValue()
                .setIfAbsent("idem:" + idempotencyKey, "1", Duration.ofSeconds(30));
        if (Boolean.FALSE.equals(first)) {
            throw new BizException(BizCode.DUPLICATE_REQUEST, "重复提交, 请勿重试");
        }
        try {
            return orderRepo.save(cmd.toOrder()).getId();   // 真正的业务只可能发生一次
        } catch (Exception e) {
            redis.delete("idem:" + idempotencyKey);         // 业务失败要还 token, 允许用户重试
            throw e;
        }
    }
}
```

```sql
-- 方案二：数据库唯一索引 —— 最后防线, 前面全被绕过它也能兜住
-- 业务唯一键建唯一索引, 重复插入直接报 DuplicateKeyException → 捕获后转"重复提交"
ALTER TABLE t_order ADD UNIQUE KEY uk_user_product (user_id, product_id, created_date);
```

- 方案三（状态机）：只允许合法状态迁移——`WHERE status = 'CREATED'` 的 UPDATE 天然拒绝第二次。
- 实践组合：**入口 token 拦一道 + 库表唯一索引兜底**，状态机用于业务流转本身。

#### 2.4 分页规范

```java
// 页码式：后台管理友好（能跳页）, 但深翻页 offset 越大越慢
// 字段约定: page / size / total / hasNext —— hasNext 免得前端自己算 total > page*size
public record PageResp<T>(List<T> items, long total, int page, int size, boolean hasNext) {}

// 游标式：返回 nextCursor, 客户端带上继续拉 —— 无限滚动 / App 信息流标配
// 字段约定: cursor(本次起点) / nextCursor / hasMore —— nextCursor 为 null 即没有更多
public record CursorResp<T>(List<T> items, String cursor, String nextCursor, boolean hasMore) {}
```

> 深翻页用游标（`WHERE id > :cursor ORDER BY id LIMIT 20`，索引范围内扫描）；后台用页码。别用页码做深翻页——第 10 万页的 `LIMIT 999980, 20` 是慢查询制造机（阶段三第 1 讲回收）。

### 3. 接口文档：SpringDoc / OpenAPI

```java
// SpringDoc（OpenAPI 3.0）注解直接长在 Controller 上（对标 @nestjs/swagger）
@Tag(name = "订单接口")                                 // 分组
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    @Operation(summary = "创建订单", description = "幂等：重复提交返回 40001")
    @PostMapping
    public ApiResponse<Long> create(@RequestBody @Valid CreateOrderCmd cmd) { ... }
}
```

```xml
<!-- pom.xml：一个依赖换来自动文档 + 调试 UI（/swagger-ui.html） -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.0</version>
</dependency>
```

进阶：OpenAPI 契约先行（先定 schema → 前端生成 TS 类型并行开发）→ 阶段七第 1 讲「API 契约管理」展开。

### 4. 文件上传与 CORS

```java
@PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public String upload(@RequestParam MultipartFile file) throws IOException {
    // ⚠️ 安全校验四件套(后缀/MIME/魔数/大小, 见代码块下方) + 存储文件名服务端生成
    String safeName = UUID.randomUUID() + ".jpg";
    Path target = Path.of("/data/upload", safeName);      // 拒绝客户端传文件名 —— 防 "../../" 路径穿越
    Files.copy(file.getInputStream(), target);
    return safeName;
}
```

上传校验四件套，每条一句话：

- **后缀白名单**：只放行 `.jpg` / `.png` 等白名单后缀——黑名单思维迟早漏掉新变种。
- **MIME 类型**：`file.getContentType()` 必须命中白名单——但它由客户端上报、可伪造，不能单独作准。
- **文件头魔数**：读文件前几个字节验证真实类型（JPEG 开头 `FF D8`，PNG 开头 `89 50 4E 47`）——后缀和 MIME 都能骗人，魔数不能。
- **大小上限**：`spring.servlet.multipart.max-file-size` 先限一道，代码里再校验一次——防恶意大文件打爆磁盘 / 内存。

```java
// CORS 全局配置（对照 NestJS 的 app.enableCors()）
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")   // 本地前端 dev server
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .maxAge(3600);                              // 预检结果缓存 1h, 减少 OPTIONS 往返
    }
}
```

> 预检（OPTIONS）：非简单请求（自定义 Header / JSON POST 跨域）会先发 OPTIONS 试探——排查「接口没进 Controller 却返回 403」时先想到它。

### 坑点提醒

- `@RequestBody` 与 `@RequestParam` 不能同用一个参数来源：前者读 body 流（只能读一次），后者读 URL / 表单。
- 校验注解写在 `Map` / `String` 裸参数上不生效——**先包 DTO**，校验才有落点。
- GET 语义别承载写操作：浏览器预取 / 爬虫 / 代理缓存都会替你「执行」它。
- 幂等 token 的 Redis key 过期时间别设太长：用户 5 分钟后重试是合理行为，不该被拒。
- 全局异常处理器别把堆栈原样返回给前端（信息泄露）；日志里打全，响应里给「人话」。

## 本节自检

- [ ] 能搭出 `@RestControllerAdvice` 统一异常转码，让所有错误响应走同一结构
- [ ] 能为一个「创建订单」接口设计幂等方案（token / 唯一索引 / 状态机至少组合两种）
- [ ] 能说清路径版本与 Header 版本的取舍，以及深翻页为什么用游标
- [ ] 能用 SpringDoc 给接口加注解并生成可调试文档

## 本节配套思考题

1. 前端同时调 `POST /orders` 五次（网络重试），你的 token 幂等方案里 `setIfAbsent` 的过期时间取 3 秒 / 30 秒 / 10 分钟分别会出什么问题？
2. 为什么「错误码分段」（4xxxx / 5xxxx / 9xxxx）对前端和 On-Call 都重要？如果只有 `code=1/2/3` 会丢掉什么信息？
3. 用 NestJS 的思路口述一遍 Spring 这套「DTO 校验 → 全局异常 → 统一响应」链路，指出两个框架里各环节的对应物。
