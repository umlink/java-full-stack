# ADR-004：本地 Outbox 与 RocketMQ 延时关单

日期：2026-10-10
状态：已采纳

## 决策

M2.5-03 使用 Apache RocketMQ 5.5.1 的普通 Java remoting client，不引入依赖 Spring Boot 内部扩展点的 starter。订单创建事务同时写订单、明细、数据库库存锁定和 `order-timeout` v1 Outbox；独立调度器在事务外发送延时消息。生产默认使用 RocketMQ 延时等级 16（30 分钟），测试使用等级 1（1 秒）。

Outbox 以稳定业务键 `order-timeout:v1:{orderId}` 唯一约束防止重复建任务。消息载荷携带事件 ID、业务键、订单 ID、版本和创建时间，RocketMQ message key 使用业务键，tag 固定为版本化事件类型。发送失败按配置的次数与退避时间重试，超过上限进入 `DEAD`；发送返回成功但数据库尚未标记时允许重复发送，由消费幂等吸收。

消费者以 Outbox 终态和订单条件状态更新作为双重幂等边界。在同一本地事务内，只有 `CREATED` 订单能转为 `CANCELLED` 并释放数据库锁定库存，随后把事件标记为 `PROCESSED`；`PAID`、`CANCELLED`、`SHIPPED` 或 `DONE` 只把事件标记为 `IGNORED`。消费失败抛回客户端触发 RocketMQ 有界重试，超过上限由 Broker 放入死信队列。Redis 库存释放仍由 M2.5-02 的数据库提交后回调完成，失败交给 M2.5-04 对账。

## 触发信号与备选

卡片要求订单创建后可靠地产生延时关单任务，并在至少一次投递下保持幂等。

- 在订单事务内直接发送：实现少，但数据库提交与 MQ 发送之间存在双写窗口，而且事务会持有数据库资源等待网络。
- RocketMQ 事务消息：能缩小部分双写窗口，但需要本地事务回查协议，仍需业务幂等和失败治理，超过本卡单体学习范围。
- 本地 Outbox：新增表与轮询成本，但订单事务只依赖本地数据库，发送失败有可查询事实，最适合当前单库模块化单体。

## 兼容性、影响与回退

RocketMQ 5.5.1 server 与 `rocketmq-client` 5.5.1 同版本验证；普通 client 避免 RocketMQ Spring starter 对 Spring Boot 4 自动装配兼容性的额外风险。经典延时等级由 Broker 定义，等级 16 为 30 分钟，等级 1 为 1 秒。

关闭 `BOOT_ROCKETMQ_ENABLED` 后不创建生产者、消费者或调度器，订单仍写 Outbox，超时任务暂停发送但不会丢失。恢复 RocketMQ 并重新开启后，未达到失败上限的记录继续发送；`DEAD` 记录需定位原因后显式恢复。回退依赖和运行组件不会改写订单、库存或支付事实。
