# 阶段三 · 小点 3：ORM 与数据访问（MyBatis / JPA / jOOQ）

> 所属：阶段三 数据持久化与中间件
> 定位：国内企业主流是 MyBatis-Plus，标准是 JPA，类型安全选项是 jOOQ——三者按「SQL 掌控力 ↔ 开发效率」的光谱分布。你写过 TypeORM / Prisma，概念可全量迁移；本讲补齐三件套的写法体感与选型判断。

## 快速入门

> 本节为「ORM 速览」：先认识 Java 访问数据库的几种方式、各自取舍；「N+1、多数据源」留在正文提高部分。

### 本讲核心关键词速查

| 关键词 | 一句话大白话 | 例子 |
|-|-|-|
| ORM | 用对象来操作数据库（对象关系映射） | 不用写 SQL，直接操作对象 |
| MyBatis-Plus | 国内主流，SQL 可控 + 易用 | 增删改查一行搞定 |
| JPA | Java 标准 ORM（Hibernate 实现） | `Repository` 接口 |
| jOOQ | 类型安全的 SQL DSL | 编译期检查 SQL 对错 |
| Entity | 映射到数据库表的 Java 类 | `@Table` 标注的类 |
| Repository | 数据访问接口 | `findById` 等方法 |
| N+1 问题 | 查 1 次列表 + 每行又查一次关联 | JPA 经典坑 |
| Mapper | MyBatis 的数据访问接口 | 无实现类也能跑 |

### 本讲在解决什么问题

- **问题**：Java 怎么操作数据库？是手写 SQL（MyBatis）、还是操作对象（JPA）、还是类型安全的 SQL（jOOQ）？三者在「SQL 掌控力 ↔ 开发效率」光谱上各有取舍。
- **你要带走的一句话**：**MyBatis-Plus** 国内主流（SQL 可控 + 好用）、**JPA** 是标准（领域建模省心）、**jOOQ** 类型安全（复杂查询编译期对错）。别在一套项目里混两种以上。

### 最简可运行示例（照抄能跑）

```java
// MyBatis-Plus: 用条件构造器查数据(不用手写 SQL)
@Service
public class UserService {
    private final UserMapper userMapper;       // MyBatis 的 Mapper 接口, 无实现类也能跑(动态代理)

    public List<User> findActive() {
        return userMapper.selectList(
            new LambdaQueryWrapper<User>()
                .eq(User::getStatus, 1));        // WHERE status = 1
    }
}
```

```java
// Spring Data JPA: 继承 Repository 接口, 方法名即查询
public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findByStatus(int status);        // 方法名: SELECT * FROM user WHERE status=?
}
```

> 代码备注（逐行解释）：
> - MyBatis-Plus 的 `userMapper`：Mapped 接口**不用写实现类**——由动态代理（阶段一第 3 讲）生成，这就是「Mapper 能跑」的原因。
> - `new LambdaQueryWrapper<User>().eq(...)`：用条件构造器拼查询条件，比手写 SQL 安全又不失控制。
> - JPA 的 `findByStatus(int)`：**方法名即查询**（Spring Data 按命名规则自动生成 SQL）——这是 JPA 最省事的地方。
> - 二者差异：MyBatis-Plus 显式、SQL 掌控强；JPA 隐式、靠命名规范，省心但底层不透明。

### 关键概念 / 技术说明

| 概念 | 干什么 | 最易踩的坑 |
|-|-|-|
| `@Table` / `@Entity` | 标注映射到表的类 | 表名字段要和实体对应 |
| `@Mapper` | MyBatis 数据接口 | 要能被扫描到 |
| `JpaRepository<T, ID>` | 标准 CRUD 接口 | 方法命名规范要记住 |
| `@EntityGraph` | 预加载关联（解 N+1） | 忘用会触发 N+1 |
| N+1 问题 | 列表查询逐条查关联 | 用 `JOIN FETCH`/`@EntityGraph` 解 |
| `ddl-auto` | JPA 自动建表 | 生产**禁用** `update`，用 Flyway 管表结构 |

### 常用约定 / 命名提示

- **DTO↔Entity 用 MapStruct**：编译期生成转换代码，别用 `BeanUtils` 反射拷贝（慢、字段改名不报错）。
- **表结构变更用 Flyway**：JPA 的 `ddl-auto` 在开发可以，生产禁用（唯一事实源是迁移脚本）。
- **选型别混**：一个项目尽量用一种 ORM（MyBatis-Plus 或 JPA 二选一为主），别混两套以上。

## 精简大纲

1. ORM 心智模型与三家定位光谱
2. MyBatis / MyBatis-Plus：国内主流必学
3. Spring Data JPA：Repository 抽象与 N+1 问题
4. jOOQ：类型安全的 SQL DSL
5. 多数据源与读写分离落地

## 学习内容详情

### 1. ORM 心智模型与选型光谱

- **ORM**（**Object-Relational Mapping**，对象关系映射：把「Java 对象 ↔ 数据库表行」的转换自动化，让你操作对象而不是拼 SQL 字符串）：概念与 TypeORM / Prisma 完全同构。
- 三家定位：

```text
SQL 掌控力 ←—————————————————————→ 开发效率
  MyBatis(-Plus)      jOOQ            JPA/Hibernate
  SQL 自己写           SQL 类型安全生成    方法名自动生成 SQL
  国内企业主流         中小团队上升中      国际标准/快速原型
```

选型决策表：

| 你的情况 | 选 |
|-|-|
| 团队 SQL 文化强、要可控 SQL | MyBatis-Plus |
| 领域建模、标准 CRUD 为主 | Spring Data JPA |
| 类型安全 + 复杂查询编译期校验 | jOOQ |
| 任何情况 | **不要在一个项目里混用两种以上**——事务、缓存、审计各自为政，排障成本翻倍 |

### 2. MyBatis / MyBatis-Plus

#### 2.1 MyBatis 本体：SQL 写在 XML 里

```xml
<!-- OrderMapper.xml：SQL 与 Java 分离，动态 SQL 是它的灵魂 -->
<mapper namespace="com.demo.mapper.OrderMapper">

    <!-- ${} vs #{} 是安全分界线:
         #{} → 预编译参数占位符(PreparedStatement 的 ?)，防 SQL 注入，默认永远用它
         ${} → 字符串原样拼接，仅限"排序字段名"这类白名单值 -->
    <select id="selectByUser" resultType="Order">
        SELECT id, order_no, user_id, status, amount
        FROM `order`
        WHERE user_id = #{userId}
        <!-- 动态 SQL: 条件成立才拼进语句 —— 前端表单多条件筛选的标配 -->
        <if test="status != null">
            AND status = #{status}
        </if>
        <!-- foreach: 批量 IN 查询, 大量 ID 时比 OR 高效得多 -->
        <if test="orderNos != null and orderNos.size() > 0">
            AND order_no IN
            <foreach collection="orderNos" item="no" open="(" separator="," close=")">
                #{no}
            </foreach>
        </if>
    </select>
</mapper>
```

#### 2.2 MyBatis-Plus：把单表 CRUD 从 XML 里解放出来

```java
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

// 实体：注解映射表结构（对应第 1 讲建好的 order 表）
@TableName("`order`")                        // order 是 SQL 关键字, 要反引号转义
public class Order {
    @TableId(type = IdType.ASSIGN_ID)        // ASSIGN_ID = 内置雪花算法生成分布式 ID
    private Long id;                         // (分库分表场景自增 ID 会撞, 雪花是标配)
    private String orderNo;
    private Long userId;
    private Integer status;
    private BigDecimal amount;

    @TableLogic                              // 逻辑删除: DELETE 变 UPDATE deleted=1
    private Integer deleted;                 // 查询自动拼 AND deleted=0, 业务无感

    @TableField(fill = FieldFill.INSERT)     // 自动填充: 创建时间交给框架, 不在每个方法里手写
    private LocalDateTime createdAt;
}
```

```java
// Mapper：继承 BaseMapper 即获得全部单表 CRUD，零 XML
public interface OrderMapper extends BaseMapper<Order> {}

// Service 里直接用条件构造器（类型安全的 Lambda 版，列名写错编译期就报错）
@Service
public class OrderQueryService {
    private final OrderMapper orderMapper;
    public OrderQueryService(OrderMapper orderMapper) { this.orderMapper = orderMapper; }

    public List<Order> paidOrdersOf(Long userId) {
        return orderMapper.selectList(
            new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)          // WHERE user_id = ?
                .eq(Order::getStatus, 1)               // AND status = 1 —— 已支付
                .orderByDesc(Order::getCreatedAt)      // ORDER BY created_at DESC
                .last("LIMIT 20"));                    // last() 原样拼接: 只能放无注入风险的常量
    }
}
```

```java
// 分页插件：物理分页(自动改写成 LIMIT offset,size)而非内存分页
@Configuration
public class MybatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(
            new PaginationInnerInterceptor(DbType.MYSQL));  // 拦截分页语句改写 SQL
        return interceptor;
    }
}
// 用法: orderMapper.selectPage(new Page<>(2, 20), wrapper) → 第2页每页20条
```

```java
// 自动填充：MetaObjectHandler 在 INSERT/UPDATE 时统一塞审计字段
@Component
public class AuditFillHandler implements MetaObjectHandler {
    @Override
    public void insertFill(MetaObject meta) {
        this.strictInsertFill(meta, "createdAt", LocalDateTime.class, LocalDateTime.now());
    }
    @Override
    public void updateFill(MetaObject meta) {
        this.strictUpdateFill(meta, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
```

### 3. Spring Data JPA

#### 3.1 Repository：方法名即查询

```java
// Spring Data JPA：接口方法名派生查询 —— 不写 SQL，框架按方法名生成
public interface OrderRepository extends JpaRepository<Order, Long> {

    // 方法名就是查询语义: SELECT ... WHERE user_id = ? AND status = ?
    List<Order> findByUserIdAndStatus(Long userId, Integer status);

    // 复杂查询用 @Query(JPQL, 面向实体不是表)
    @Query("SELECT o FROM Order o WHERE o.amount > :min ORDER BY o.amount DESC")
    List<Order> findBigOrders(@Param("min") BigDecimal min);
}
```

- **JPA / Hibernate 关系**（**JPA** 是 Java 持久化 API **标准**，**Hibernate** 是其默认**实现**）：类比 SLF4J（门面）与 Logback（实现）。
- 好处：从 MySQL 迁 PG 几乎零改动（方言屏蔽）；代价：复杂 SQL 要跟框架搏斗（JPQL 语法受限、优化器黑盒）。
- **实体生命周期**（一句话：实体不是静态的 Java 对象，它在持久化上下文里换状态）——四态一行表：

| new / transient | managed | detached | removed |
|-|-|-|-|
| 刚 new 出来，未关联上下文 | 受管：脏检查自动同步 DB | 上下文关闭后脱管 | 标记删除，flush 时发 DELETE |

- **一级缓存**：同一持久化上下文里同 ID 只有一个受管实例——事务内重复 `findById` 不打第二条 SQL。

#### 3.2 N+1 问题：JPA 的头号坑

```java
// 场景: 查 100 个用户, 再逐个取他们的订单(关联是 LAZY 懒加载)
List<User> users = userRepository.findAll();      // ① 1 条 SQL: SELECT * FROM user
for (User u : users) {
    u.getOrders().size();                          // ② 每个用户首次访问订单 → 再发 1 条 SQL!
}
// 结果: 1 + 100 = 101 条 SQL —— 就是 N+1 问题
// (TypeORM/Prisma 一样有, 前端同学 migration 时最容易带进来的坑)
```

```java
// 解法一: @EntityGraph —— 一条 JOIN SQL 把关联一起取出来(前端类比: include/with 预加载)
public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "items")          // 生成 LEFT JOIN order_item, 一次取齐
    List<Order> findByUserId(Long userId);
}

// 解法二: DTO 投影 —— 直接 SELECT 需要的列, 不加载实体(最彻底, 报表类查询首选)
@Query("SELECT new com.demo.dto.OrderBrief(o.id, o.orderNo, o.amount) FROM Order o WHERE o.userId = :uid")
List<OrderBrief> findBriefByUserId(@Param("uid") Long uid);
```

#### 3.3 数据库变更管理：Flyway

- **为什么需要**：生产禁手工跑 DDL——变更要**可审查**（SQL 进代码评审）、**可重复**（新环境一键建到最新结构）、**可追溯**（哪个版本改了什么，回滚有据）。
- **约定**：脚本放 `src/main/resources/db/migration`，文件名即版本：`V1__init.sql`、`V2__add_order_status.sql`——Boot 启动时按序执行没跑过的，已跑的记录在 `flyway_schema_history` 表。
- **Boot 集成**：引入 `flyway-mysql` 依赖即自动执行（具体版本与配置以官方文档为准）。
- **与 JPA 的关系**：**禁用 `ddl-auto=update`**——表结构的唯一事实源是迁移脚本，实体只做映射不建表。

### 4. jOOQ：类型安全的 SQL DSL

```java
// jOOQ: 代码生成器按表结构生成强类型对象(ORDER 表、ORDER.USER_ID 列都是类型安全的常量)
// 写错列名 → 编译报错; 重命名列 → 重新生成后编译报错 —— "SQL 的 TypeScript"
Result<Record> rows = dsl
    .select(ORDER.ID, ORDER.ORDER_NO, ORDER.AMOUNT)
    .from(ORDER)
    .where(ORDER.USER_ID.eq(uid))                  // .eq 只接受 Long, 传 String 编译不过
    .and(ORDER.STATUS.eq(1))
    .orderBy(ORDER.CREATED_AT.desc())
    .limit(20)
    .fetch();                                      // 生成的 SQL 与手写等价, 无魔法
```

- 心智迁移：与 Prisma 的「schema 生成客户端」同宗——**契约由数据库结构出发，编译期守护**。适合喜欢类型安全的你；国内存量少，了解 + 试用即可。

### 5. 多数据源与读写分离落地

```java
// 动态数据源路由: AbstractRoutingDataSource 按 ThreadLocal 标记决定本次连接走主库还是从库
public class DynamicDataSource extends AbstractRoutingDataSource {
    public static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    @Override
    protected Object determineCurrentLookupKey() {
        return HOLDER.get();        // "master" / "slave" —— 拿连接时调用
    }
}

// AOP 切面: 标了 @ReadOnly 的方法自动切从库
@Aspect
@Component
public class ReadOnlyAspect {
    @Around("@annotation(readOnly)")
    public Object route(ProceedingJoinPoint pjp, ReadOnly readOnly) throws Throwable {
        DynamicDataSource.HOLDER.set("slave");      // 读标记 → 从库
        try {
            return pjp.proceed();
        } finally {
            DynamicDataSource.HOLDER.remove();      // 必须 remove! 线程池复用, 泄漏标记会污染下一请求
        }
    }
}
// 写方法不标 → 默认 master。第 2 讲「写后读主」= 写完短窗口 set("master") + 最后 remove
```

### 6. 坑点提醒

- **`${}` 只准出现在排序字段这类白名单**：任何来自用户的值过 `${}` 都是注入入口；页面排序字段用枚举白名单映射。
- **MyBatis-Plus 的 `last()`**：拼什么进什么，等价 `${}` 纪律。
- **JPA 默认 LAZY + Controller 里访问关联**：事务已关闭还触发加载 → `LazyInitializationException`（session 早没了）；要么 `@EntityGraph`，要么在事务内取完。
- **逻辑删除的盲区**：唯一索引会被「已删数据」占坑（删了又建同 order_no 冲突）——唯一索引要包含 deleted 列或用「删除时间戳」当 deleted 值。
- **自动填充只对 MP 自己的 INSERT/UPDATE 生效**：手写 XML 的原生 SQL 不会触发填充，混用时要留心。
- **DTO↔Entity 转换用 MapStruct**（编译期生成转换代码）：别用 `BeanUtils.copyProperties` 反射拷贝——慢，且字段改名不报错、悄悄丢值。

## 本节自检

- [ ] 能用 MyBatis-Plus 完成单表 CRUD + 分页 + 逻辑删除 + 自动填充的完整配置
- [ ] 能解释 N+1 问题怎么产生，并用 `@EntityGraph` 或 DTO 投影解决
- [ ] 能说出三者（MyBatis-Plus / JPA / jOOQ）在「SQL 掌控力 ↔ 开发效率」光谱上的位置与选型建议
- [ ] 能描述动态数据源路由的实现思路（AbstractRoutingDataSource + ThreadLocal + AOP）以及 finally remove 的原因

## 本节配套思考题

1. 同样是「对象 ↔ 表」映射，为什么国内选 MyBatis 而欧美更多 JPA？（提示：DBA 文化 / SQL 审核流程 / 复杂报表场景的差异）
2. `@TableLogic` 逻辑删除后，「订单号唯一」的语义怎么保证？给出至少两种方案并说明取舍。
3. 如果项目 80% 是简单 CRUD、20% 是复杂报表，你会怎么组合这两讲里的工具？（提示：MP 走日常 + jOOQ/原生 SQL 走报表）
