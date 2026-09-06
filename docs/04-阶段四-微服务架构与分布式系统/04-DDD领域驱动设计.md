# 阶段四 · 小点 4：DDD 领域驱动设计

> 所属：阶段四 微服务架构与分布式系统
> 定位：DDD 回答的是微服务最贵的问题——**服务边界怎么划**（拆错了要还很久的债）。它同时也是模块化单体的设计方法论：边界思维与拆不拆物理服务无关。

> 学习顺序提示：限界上下文之间怎么协作（同步 RPC / 异步事件）在第 5 讲展开——建议先把第 5 讲的通信小节扫一遍再回读本讲，边界感会更实。

## 快速入门

> 本节为「DDD 速览」：先认识 DDD 解决什么问题、几个核心概念；「四层架构、事件风暴」留在正文提高部分。

### 本讲核心关键词速查

| 关键词 | 一句话大白话 | 例子 |
|-|-|-|
| DDD | 领域驱动设计：按业务领域建模 | 电商分成订单、库存等领域 |
| 限界上下文 | 一个业务领域的边界 | 订单上下文、库存上下文 |
| 聚合根 | 一致性边界的入口对象 | `Order` 聚合根 |
| 实体 | 有唯一标识、会变化的对象 | 订单有 id、状态会变 |
| 值对象 | 不可变、按值判等 | 金额、地址 |
| 领域事件 | 领域内发生的事 | `OrderCreated` |
| 防腐层 | 隔离上下文的转换层 | 保护领域模型不被外部污染 |
| 贫血模型 | 只有字段没有行为的模型 | 反模式 |

### 本讲在解决什么问题

- **问题**：微服务最贵的问题是「服务边界怎么划」——拆错了要还很久的债。DDD 教你怎么按业务领域建模、划边界。
- **你要带走的一句话**：DDD 的**限界上下文** ≈ 服务边界。它同时也是模块化单体的方法论——**边界思维与拆不拆物理服务无关**。先把边界划清，再决定要不要物理拆。

### 最简可运行示例（照抄能跑）

```java
// 聚合根: 一致性边界的入口, 业务规则都从这里收口
public class Order {
    private Long id;
    private List<OrderItem> items;      // 订单项(值对象/实体)
    private OrderStatus status;

    // 业务规则收在聚合根里, 而不是散在 Service —— 这是 DDD 的关键
    public void cancel(String reason) {
        if (status != OrderStatus.CREATED) {
            throw new IllegalStateException("只有待支付订单能取消");   // 不变式守护
        }
        this.status = OrderStatus.CANCELLED;
        // 领域事件: 取消后发出去(让别的上下文响应)
        domainEvents.add(new OrderCancelled(id, reason));
    }
}
```

> 代码备注（逐行解释）：
> - `Order` 是**聚合根**：一致性边界的入口，包含 `items` 等子对象。
> - `cancel()` 里判断「只有待支付能取消」——这叫**不变式守护**：业务规则收在聚合根内，不散在 Service。
> - `domainEvents.add(new OrderCancelled(...))`：**领域事件**——取消后发出，让别的上下文（库存、通知）响应。
> - 对比贫血模型：如果 Order 只有 getter/setter、`cancel` 判断写在 service 里，就是「贫血模型」——业务规则离领域越来越远（反模式）。

### 关键概念说明

| 概念 | 干什么 | 最易踩的坑 |
|-|-|-|
| 限界上下文 | 业务边界 | ≈ 服务边界的划分依据 |
| 聚合根 | 一致性入口 + 不变式守护 | 别把拆分和服务边界混为一谈 |
| 实体 / 值对象 | 有标识变化 / 不可变按值 | 值对象如金额、地址 |
| 领域事件 | 领域内发生的事 | 用来解耦上下文 |
| 防腐层 | 隔离外部模型 | 保护领域不被外部污染 |
| 贫血模型 | 只有字段没行为 | 业务规则别全塞 service |
| CQRS / ES | 读写分离 / 事件溯源 | 强审计才值得，复杂度高 |

### 常用约定 / 命名提示

- **边界思维 ≠ 物理拆服务**：DDD 是模块化单体的方法论——先划清边界，再决定拆不拆，别上来就拆微服务。
- **别过度设计**：CQRS / Event Sourcing（事件溯源）只在「强审计、需要历史回放」时才用；一般先说「操作日志表」够不够。
- **贫血模型是反模式**：业务规则（状态校验、权限判断）应收进聚合根，而不是散在 Service 的 `if-else` 里。

## 精简大纲

1. 核心概念：领域 / 子域 / 限界上下文 / 聚合根 / 实体 / 值对象 / 领域事件 / 防腐层
2. 四层架构与依赖倒置的 Java 落地
3. 事件风暴：从业务事件到服务边界
4. CQRS 与 Event Sourcing 的收益与成本
5. 与微服务的关系：限界上下文 ≈ 边界；Modulith 是第一步

## 学习内容详情

### 1. 核心概念

| 概念 | 一句话 | 前端类比 |
|-|-|-|
| 领域 / 子域 | 业务问题空间及其拆分（核心 / 支撑 / 通用子域） | 产品域划分 |
| **限界上下文** | 一个模型成立的边界（「订单」在交易与物流上下文里含义不同） | BFF 的领域分界 |
| **聚合根** | 一致性边界的入口对象，外部只能通过它操作内部 | Store / 受控容器组件 |
| 实体 | 有唯一标识、生命周期内可变（Order） | 有 id 的实体对象 |
| 值对象 | 无标识、不可变、按值判等——**Java 里 record 的天职** | 不可变 props |
| 领域事件 | 领域内发生的有业务含义的事实（OrderPlaced） | EventBus 的 payload |
| 防腐层（ACL） | 上下文间转换模型的隔离层，防外部模型入侵 | API adapter / mapper 层 |

```java
// 值对象：record 天生就是它该成为的样子（阶段一第 2 讲伏笔回收）
public record Money(long cents, Currency currency) {
    public Money add(Money other) {                      // 不可变: "修改"返回新对象
        if (!currency.equals(other.currency)) throw new IllegalArgumentException("币种不同不可相加");
        return new Money(cents + other.cents, currency); // 校验长在类型里 —— 非法状态根本表达不出来
    }
}
// 对比"传统做法": BigDecimal + String 散落各处校验 —— 同样的规则, DDD 把它关进了类型的笼子
```

**聚合**的纪律：聚合根守住不变式（如「订单总金额 = Σ 明细」），外部**不得绕过根直接改内部对象**；聚合要小——大聚合 = 锁粒度大 + 并发差，一致性边界内收、边界外交给事件（最终一致）。

### 2. 四层架构与依赖倒置

```text
interfaces/    ← 协议转换(DTO ↔ Cmd/Query), 不含规则
application/   ← 用例编排(找聚合 → 调方法 → 发事件 → 提交事务), 不含规则
domain/        ← 业务规则唯一所在地(聚合/值对象/领域服务/Repository 接口) ★
infrastructure/← 技术实现(Repository 实现/JPA/MQ/外部调用), 依赖 domain
```

```java
// 依赖倒置的具体形态: 接口在 domain, 实现在 infrastructure —— 箭头全部指向 domain
// domain/order/OrderRepository.java
public interface OrderRepository {
    Order findById(OrderId id);
    void save(Order order);
}

// infrastructure/order/OrderRepositoryImpl.java —— Spring 装配时替换接口
@Repository
public class OrderRepositoryImpl implements OrderRepository {
    private final OrderJpa entityDao;                      // JPA 细节只出现在这一层
    public Order findById(OrderId id) {
        return entityDao.findById(id.value()).map(OrderMapper::toDomain).orElseThrow();
    }
    /* ... */
}

// application/PlaceOrderService.java —— 用例只做编排
@Service
class PlaceOrderService {
    @Transactional
    public OrderId place(PlaceOrderCmd cmd) {
        var order = Order.create(cmd.userId(), cmd.items());   // 聚合根静态工厂: 不变式在构造处守住
        orderRepo.save(order);
        eventPublisher.publishEvent(new OrderPlacedEvent(order.id(), order.total()));
        return order.id();
    }
}
```

- **判断业务规则放哪**：出现在两个用例里的规则、会随业务变化的规则 → domain；「怎么存 / 怎么发」→ infrastructure；「先做什么后做什么」→ application。
- 红线：**domain 层零框架依赖**（没有 JPA / Spring 注解）——这是「领域可单测、可换存储」的前提。

### 3. 事件风暴

```text
流程: 业务事件(橙) → 谁触发命令(蓝) → 命令作用于哪个聚合(黄)
      → 聚合聚类成限界上下文 → 上下文间映射(上下游/ACL/共享内核)

产出物: 事件流时间线 + 上下文地图 —— 服务边界由业务事实决定, 而非技术直觉
```

- 做法：跨职能工作坊，便利贴墙上贴——**业务方参与**是重点（不是架构师自嗨）；先铺「已发生的事实」（过去式：订单已支付），再反推命令与聚合。
- 一个信号：如果一种「订单」便利贴被两类团队同时用不同字段描述——限界上下文在这里，拆点就在这。

### 4. CQRS 与 Event Sourcing

- **CQRS（命令查询职责分离）**：写侧走聚合 + 事务保不变式；读侧绕开聚合直接查宽表 / ES（阶段三第 6 讲）——**读写负载与模型需求分化**时收益明显；查询要求高的报表 / 列表是主战场。
- **Event Sourcing（事件溯源）**：不存当前状态，存**事件序列**（OrderPlaced → ItemAdded → Paid），状态 = 回放。收益：完整审计、时间旅行（任意时点重放）、事件即集成；成本：快照优化、事件 schema 演进（老事件永远要能解）、查询必须另建读模型（通常配 CQRS）。**正向适用场景**：强审计合规（金融、医疗）、需要完整历史回放与状态回滚时才值得上——不是越核心越该用。
- 务实建议：**CQRS 可以局部用**（某个读重模块）；**ES 慎用**。事件溯源 vs 操作日志表：先问要的是「审计证据」还是「状态重建能力」——只要审计，操作日志表（+ 状态流水）成本低一个量级。

### 5. 与微服务的关系

- **限界上下文 ≈ 微服务边界**：上下文间只通过事件 / 显式接口协作——这正是「微服务自治」的定义来源。
- **模块化单体（Spring Modulith）是 DDD 的第一步落地形态**：单体内按上下文分模块、模块间事件通信 + 架构测试守边界（`ApplicationModules` 单测验证依赖方向），**验证边界成熟后再拆出独立服务**——呼应第 1 讲「能单体的先单体」，拆分从赌注变成水到渠成。

### 坑点提醒

- **贫血模型**：Service 里写满 if-else、domain 对象只剩 getter/setter——DDD 名号下干的还是事务脚本。判据：**业务规则离 domain 层越远越贫血**。
- **聚合当查询模型用**：为了列表页把全表装进聚合——聚合是一致性边界不是 ORM 实体；读走查询侧（CQRS 思维）。
- **防腐层双向渗透**：ACL 里直接复用对方的 DTO 当自己的模型——转换没发生，外部模型照样入侵。
- **微服务粒度过早对齐「一个聚合一个服务」**：上下文 ≠ 部署单元——先逻辑分模块，物理拆分按团队与流量信号（第 1 讲的触发信号）。

## 本节自检

- [ ] 能用自己的话解释限界上下文与聚合根，并各举一个业务例子
- [ ] 能画出 DDD 四层并说明依赖倒置在 Repository 接口 / 实现上怎么落地
- [ ] 能描述事件风暴的流程，以及它为什么能让「服务边界由业务事实决定」
- [ ] 能说出 CQRS 与 ES 各自的收益与成本，以及为什么 ES 要慎用
- [ ] 能判断一段代码是贫血还是充血模型并给出重构方向

## 本节配套思考题

1. 电商「商品」在商品上下文与交易上下文里的模型差异是什么？复制字段还是共享引用？决策依据？
2. 你的中后台项目（阶段七项目一）用 Modulith 分模块，ArchUnit 测试该断言哪三条依赖规则才算守住边界？
3. 「一个聚合一次事务，跨聚合靠事件最终一致」——下单扣库存同库时还需要 Seata 吗？什么时候「同库多聚合」的诱惑会让一致性变脆？
