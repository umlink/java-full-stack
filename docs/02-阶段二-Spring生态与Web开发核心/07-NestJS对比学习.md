# 阶段二 · 小点 7：与 NestJS 的对比学习

> 所属：阶段二 Spring 生态与 Web 开发核心
> 定位：把整个阶段二收进一张对照表 + 三组「同一需求的两种写法」。NestJS 大量借鉴 Spring，这张表是你最快的学习加速器——**新概念先映射旧概念，再记差异**。

## 快速入门

> 本节为「对照速查」：先把你熟悉的 NestJS 概念对位到 Spring 对应写法；「深层差异原因」留在正文提高部分。

### 本讲核心映射速查

| NestJS | Spring | 一句话对比 |
|-|-|-|
| `@Module` | `@Configuration` + `@ComponentScan` | Spring 隐式扫描，NestJS 显式声明模块 |
| `@Injectable()` | `@Service` / `@Component` | 都是「交给容器管理」 |
| `@Controller` | `@RestController` | 都是处理 HTTP 请求 |
| `@UseGuards(Guard)` | 过滤器链 + `@PreAuthorize` | 鉴权是「链 + 方法级注解」组合 |
| `Pipe`（如 ParseIntPipe） | Converter + Bean Validation | 参数转换 + 校验分两个机制 |
| `Interceptor` | Spring AOP（切面） | AOP 在字节码代理层，覆盖更广 |
| ExceptionFilter | `@RestControllerAdvice` | 全局异常统一处理 |
| 生命周期钩子 | Bean 生命周期回调 | `@PostConstruct` / `@PreDestroy` |
| EventEmitter | ApplicationEvent | Spring 事件长在事务上 |

### 本讲在解决什么问题

- **问题**：你有 NestJS 底子，Spring 的很多概念其实似曾相识——只是名字、写法不同。本讲把「同一件事的两种写法」对照起来，学得最快。
- **你要带走的一句话**：**概念是通用的，实现方式不同**。看到 Spring 里不熟的写法，先映射到熟悉的 NestJS 概念，再记差异（隐式/显式、AOP 深度、校验位置）。

### 最简对照示例（照抄能跑）

```typescript
// NestJS: 一个接口, 用 Guard 鉴权、Pipe 校验
@Controller('users')
export class UserController {
  @Get(':id')
  @UseGuards(AdminGuard)            // Guard: 鉴权
  findOne(@Param('id', ParseIntPipe) id: number) {  // Pipe: 类型转换
    return this.service.findOne(id);
  }
}
```

```java
// Spring: 同一件事, 用 @PreAuthorize + @PathVariable 完成
@RestController
@RequestMapping("/users")
public class UserController {
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")        // 方法级权限校验(≈ Guard)
    public User findOne(@PathVariable Long id) {   // 参数绑定(≈ Pipe 的转换)
        return service.findOne(id);
    }
}
```

> 代码备注（逐行解释）：
> - NestJS 的 `@UseGuards(AdminGuard)` 对应 Spring 的 `@PreAuthorize("hasRole('ADMIN')")`——都是方法级鉴权。
> - NestJS 的 `ParseIntPipe`（把参数转 int）在 Spring 由 `@PathVariable Long id` 自动完成类型转换 + 校验注解分工。
> - Spring 的鉴权是「过滤器链 + 方法级注解」组合：链先做认证（你是谁），`@PreAuthorize` 做授权（你能干什么）。
> - 一个核心差异：Spring AOP 在**字节码代理层**工作（任何 Bean 方法都能切），NestJS Interceptor 只包 Controller 路由。

### 关键映射 / 用法说明

| NestJS | Spring | 差异要点 |
|-|-|-|
| `@Module` | `@Configuration` | NestJS 模块显式，Spring 组件扫描隐式 |
| Guard | 过滤器 + `@PreAuthorize` | 鉴权是组合而不是单一 Guard |
| Pipe | Converter + Bean Validation | 参数转换 / 校验两个机制 |
| Interceptor | AOP 切面 | AOP 覆盖更广（不只 Controller） |
| ExceptionFilter | `@RestControllerAdvice` | 统一异常处理 |
| 生命周期钩子 | `@PostConstruct`/`@PreDestroy` | Bean 生命周期回调 |

### 常用约定 / 迁移提醒

- **先映射、再记差异、最后练手**：这是这套文档反复强调的对比学习法——新概念先对应熟悉的 NestJS 概念，再补差异。
- **Spring 隐式 vs NestJS 显式**：Spring 组件扫描是隐式的（同包自动扫），NestJS 模块关系是显式声明——排查「Bean 没注入」时，Spring 查扫描路径、NestJS 查模块引用。
- **别把 NestJS 心智硬搬**：如「事件」——Spring 事件长在事务上（`@TransactionalEventListener`），和 EventEmitter 的「纯发布订阅」不同。

## 精简大纲

1. 框架级概念对照表（十组核心映射）
2. 同需求双栈实现：Guard vs Filter+Interceptor / Pipe vs @Valid / Module vs 自动扫描
3. 差异的深层原因：隐式 vs 显式、AOP 深度、校验位置
4. 使用方法：先映射、再记差异、最后练手验证

## 学习内容详情

### 1. 概念对照表

| NestJS 概念 | Spring 对应 | 关键差异 |
|-------------|-------------|----------|
| `@Module` | `@Configuration` + `@ComponentScan` | Spring 自动扫描更隐式，NestJS 更显式 |
| `@Injectable` | `@Service` / `@Component` | 概念一致，Spring 派生注解更多（按分层语义） |
| `@Controller` | `@RestController` | 几乎一致 |
| Guard | Filter / Interceptor | Spring 过滤器链更灵活、粒度更细 |
| Pipe | `@Valid` + Bean Validation | 标准化注解校验，生态更成熟 |
| Interceptor | HandlerInterceptor / AOP | AOP 能力远超 NestJS Interceptor |
| `ExceptionFilter` | `@RestControllerAdvice` + `@ExceptionHandler` | 概念一致：全局兜底、统一转 HTTP 响应 |
| TypeORM / Prisma | Spring Data JPA / MyBatis-Plus | JPA 是标准，MyBatis 对 SQL 掌控更直接 |
| Jest + Supertest | JUnit 5 + Mockito + Testcontainers | Java 测试工具链更重但更工程化 |
| 生命周期钩子（`onModuleInit` 等） | Bean 生命周期（`@PostConstruct` / `InitializingBean` / `@PreDestroy`） | 时机对应，Spring 粒度更细（Aware / BeanPostProcessor） |

### 2. 同需求双栈实现

#### 2.1 请求前置校验（Guard）

```typescript
// NestJS: 一个 Guard 把「谁能进这个路由」管到底
@Injectable()
export class AdminGuard implements CanActivate {
  canActivate(ctx: ExecutionContext): boolean {
    const req = ctx.switchToHttp().getRequest();
    return req.user?.role === 'admin';          // 不满足直接抛 ForbiddenException, 500 都到不了
  }
}

@Controller('admin/stats')
@UseGuards(AdminGuard)                          // 路由级挂载, 位置一目了然
export class StatsController { /* ... */ }
```

```java
// Spring: 三种「位置」任选 —— 这正是差异所在
// 位置一: 过滤器链(最外层, 认证/鉴权统一处理, 阶段二第 6 讲的 JwtAuthFilter)
// 位置二: 拦截器(HandlerInterceptor, 仅 MVC 层)
@Component
class AdminInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            res.setStatus(403);
            return false;                       // 返回 false = 请求到此为止(≈ canActivate 返回 false)
        }
        return true;
    }
}
// 位置三: 方法注解(@PreAuthorize, AOP 层 —— NestJS 没有对应物, 粒度到单个方法)
```

> NestJS 的 Guard ≈ Spring 的「Filter 链 / Interceptor / @PreAuthorize」**三者之一**——Spring 把能力拆成了更细的层次，灵活，但「这段鉴权代码该放哪层」反而成了新决策（答：横切全部请求放过滤器链，MVC 层专属放拦截器，按方法角色放 @PreAuthorize）。

#### 2.2 入参转换与校验（Pipe vs Bean Validation）

```typescript
// NestJS: ParseIntPipe 转类型, class-validator 装饰 DTO
@Get(':id')
findOne(@Param('id', ParseIntPipe) id: number) { ... }

class CreateOrderDto {
  @IsInt() @Min(1) quantity: number;
}
```

```java
// Spring: 类型转换是 Converter 体系(隐式), 校验长在 DTO 字段上(注解标准统一)
@GetMapping("/{id}")
Order detail(@PathVariable Long id) { ... }              // String→Long 框架自动转

public record CreateOrderDto(
    @Min(value = 1, message = "数量至少为 1") Integer quantity) {}
// Controller 入参加 @Valid 即触发 —— 与 Nest 的 ValidationPipe 同一时机(进方法前)
```

> 校验语义几乎一致；差异在**生态**：Bean Validation 是 JSR 标准（跨 Hibernate Validator / 未来实现可换），而 class-validator 是 NestJS 生态专属件。另记一处分工：`ParseIntPipe` 这类参数级转换在 Spring 由 Converter（类型转换）+ Bean Validation（校验）两个机制分工完成。

#### 2.3 横切日志（Interceptor vs AOP）

```typescript
// NestJS Interceptor: 包一层响应流 —— 只能拿到"可观测的进出"
@Injectable()
export class LogInterceptor implements NestInterceptor {
  intercept(ctx: ExecutionContext, next: CallHandler) {
    const t0 = Date.now();
    return next.handle().pipe(tap(() => this.logger.log(`${ctx.getClass().name} +${Date.now() - t0}ms`)));
  }
}
```

```java
// Spring AOP: 切点表达式能指到「任意方法」—— 不止 Controller, Service 内部方法也行
@Aspect @Component
public class CostLogAspect {
    @Around("@annotation(CostLog)")          // 自定义注解定位: 要切谁就标谁(比表达式白名单更可控)
    public Object log(ProceedingJoinPoint pjp) throws Throwable {
        long t0 = System.nanoTime();
        try { return pjp.proceed(); }
        finally {
            log.info("{}.{} +{}ms", pjp.getSignature().getDeclaringType().getSimpleName(),
                     pjp.getSignature().getName(), (System.nanoTime() - t0) / 1_000_000);
        }
    }
}
```

> 能力差距的本质：NestJS Interceptor 活在「HTTP 请求边界」；Spring AOP 活在「方法调用边界」（字节码代理，阶段一第 3 讲）。`@Transactional` 只能由 AOP 提供，就是这个差距最好的例证。

### 3. 深层原因（比表格更值得记）

- **隐式 vs 显式**：Spring 把「谁被注册」藏进组件扫描 / 自动装配；NestJS 的 Module 显式声明 imports / providers。调试装配问题时，Spring 查「扫描路径覆盖没有」，NestJS 查「Module 引了没有」。
- **注解语义密度**：Spring 的派生注解多（@Service / @Repository / @Controller 对容器而言是同一个 @Component，但语义分层 + MVC 特殊处理）；NestJS 的 @Injectable 一词走天下——学习期记「哪个注解在哪些框架行为里有额外含义」即可（@Controller 被 MVC 扫描、@Configuration 的 CGLIB 增强等）。
- **校验位置**：Pipe 在参数层拦截（动态、可编程）；Bean Validation 注解长在 DTO 字段上（静态、即契约）。

### 4. 使用方法

1. **先映射**：遇到 Spring 新概念，先在表中找 NestJS 对应物建立锚点。
2. **再记差异**：重点记「关键差异」列与第 3 节深层原因——直觉迁移踩坑都在这里。
3. **练手验证**：同一需求（「带 RBAC 的 CRUD 接口」）用两种栈各写一遍，差异自现。

### 坑点提醒

- **别把 NestJS 的「Guard」直译成 Spring Filter**：认证/授权/异常转码三件事在 Spring 分属过滤器链 / @PreAuthorize / @RestControllerAdvice——先问「我想拦在哪层」。
- **@Injectable 心智套到所有 @Component**：Spring 的 @Repository 等派生注解有**额外行为**（如 @Repository 触发持久化异常转译），不是纯改名。
- **module 显式声明的肌肉记忆会拖累你**：Spring 默认「同包及子包全扫」——Bean 没进容器先查扫描根路径（`@SpringBootApplication` 所在包就是扫描根），而不是找「哪个 Module 没引」。

## 本节自检

- [ ] 能默写十组概念映射，并为每组说出至少一条关键差异
- [ ] 能解释 Spring「隐式装配」与 NestJS「显式 Module」在调试排查路径上的不同
- [ ] 能说出 Spring AOP 为什么能实现 @Transactional 而 NestJS Interceptor 不能
- [ ] 能就「同一需求双栈实现」写出各自的 Guard/Pipe/Interceptor 对应代码骨架

## 本节配套思考题

1. NestJS 用 `@Module({providers: [...]})` 显式列出 provider；如果 Spring 也这么设计，会多付出什么、少踩什么坑？（想想大型工程里「为什么这个 Bean 存在 / 不存在」的可追溯性）
2. class-validator 的 `whitelist: true`（拒绝 DTO 外字段）防的是 mass assignment；Spring 侧用什么挡它（@RequestBody + record 默认忽略未知字段吗，还是要配）？
3. 给一个纯 NestJS 同事做 15 分钟 Spring 入门分享：你会选哪三组概念对照当主线？其余的为什么不讲？
