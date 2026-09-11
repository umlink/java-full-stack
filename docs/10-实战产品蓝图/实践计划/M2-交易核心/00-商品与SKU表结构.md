# M2-00：商品与SKU表结构

返回 [实践计划与进度](../README.md)。

## 目标

建立商品与 SKU 的最小数据结构和种子数据，让后续商品、购物车、订单卡片都在此结构上生长。

## 范围

- schema.sql 幂等新增 t_product（id、name、description、status 上架状态、deleted 逻辑删除、create_time、update_time）与 t_sku（id、product_id、sku_code 唯一索引、price、version 乐观锁字段、deleted、create_time、update_time）。
- 对应 MyBatis-Plus 实体与 BaseMapper。
- 种子数据若干商品与 SKU。
- 代码落 services/user-service 模块（阶段 4 拆服务前保持模块化单体）。

## 学习点

增量建模、ER 到 DDL 落地、唯一索引、乐观锁字段、MyBatis-Plus 实体映射。

## 验收

1. 空库可初始化且已有实验数据不被覆盖（schema.sql 幂等）。
2. Mapper 集成测试能查到种子商品与 SKU。
3. 重复执行 schema.sql 不产生重复数据。
4. `./mvnw -pl services/user-service -am test` 通过。

## 不做

- 不建购物车、订单、库存表（随各自卡片创建）。
- 不做任何商品接口（01/02 卡片）。
- 不做商品图片表（后置扩展）。

## 完成记录

日期：

提交：

测试与接口证据：
