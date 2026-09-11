# M2-00：商品与SKU表结构

返回 [实践计划与进度](../README.md)。

## 目标

建立商品与 SKU 的最小数据结构和种子数据，让后续商品、购物车、订单卡片都在此结构上生长。

## 范围

- schema.sql 幂等新增 t_product（id、name、description、status、deleted、create_time、update_time）与 t_sku（id、product_id、sku_code、price、version、deleted、create_time、update_time）。
- 对应 MyBatis-Plus 实体与 BaseMapper。
- 种子数据若干商品与 SKU。
- 代码落 services/user-service 模块（阶段 4 拆服务前保持模块化单体）。

## 数据不变量

以下约束是后续商品、购物车、库存和订单的共同前提，必须在 DDL 与实体映射中保持一致；金额字段使用 `BigDecimal`，禁止 `double` 或 `float`。

| 对象 | 不变量 |
|-|-|
| t_product | `id` 为主键；`name` 必填、最长 128 字符；`description` 可空；`status` 必填，取值仅为 `DRAFT`、`ON_SALE`、`OFF_SALE`，默认 `DRAFT`；`deleted` 必填，默认 `0`；创建与更新时间必填且由服务端维护。 |
| t_sku | `id` 为主键；`product_id` 必填，并以外键引用 `t_product.id`，禁止孤儿 SKU；`sku_code` 必填、最长 64 字符，并在全表全生命周期内唯一，逻辑删除后也不可复用；`price` 为 `DECIMAL(19,2)`、必填、非负；`version` 必填，默认 `0`；`deleted` 必填，默认 `0`；创建与更新时间必填且由服务端维护。 |
| 逻辑删除与唯一性 | 商品和 SKU 均使用 `deleted` 逻辑删除。`sku_code` 不把 `deleted` 拼入唯一索引：已删除 SKU 仍占用其业务编码，恢复应复用原记录，不能重新插入同编码记录。 |
| 种子数据 | 种子数据使用稳定业务键幂等写入，不按每次执行生成的新 ID 判断是否已存在；所有用于后续查询验收的 SKU 必须关联存在的商品，且商品状态与 SKU 价格符合上述约束。 |

`ON_SALE` 是后续 C 端可售查询的唯一商品状态；`DRAFT` 与 `OFF_SALE` 的查询过滤和后台改价流程属于后续卡片，本卡只固定存储枚举与默认值。

## 学习点

增量建模、ER 到 DDL 落地、唯一索引、乐观锁字段、MyBatis-Plus 实体映射。

## 验收

1. 空库可初始化且已有实验数据不被覆盖（schema.sql 幂等）。
2. Mapper 集成测试能查到种子商品与 SKU。
3. 重复执行 schema.sql 不产生重复数据，且同一 `sku_code` 即使原记录已逻辑删除也不能再次插入。
4. 集成测试覆盖：必填字段拒绝 `NULL`、商品状态拒绝枚举外值、价格拒绝负数、SKU 不能关联不存在商品、默认值按约定生效。
5. `mvnw.cmd -pl services/user-service -am test` 在 JDK 25 下通过。

## 不做

- 不建购物车、订单、库存表（随各自卡片创建）。
- 不做任何商品接口（01/02 卡片）。
- 不做商品图片表（后置扩展）。

## 完成记录

日期：

提交：

测试与接口证据：
