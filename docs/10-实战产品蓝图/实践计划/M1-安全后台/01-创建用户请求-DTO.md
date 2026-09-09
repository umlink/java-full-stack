# M1-01：创建用户请求 DTO

状态：**已完成**  前置：M1-00 已完成

返回 [M1 任务列表](README.md)。

## 目标

让 `POST /api/users` 不再直接接收持久化实体 `User`，而是接收专门描述创建行为的请求 DTO。这样客户端不能偷偷传递 `id`、`deleted` 等不应由它控制的字段。

## 范围

- 新增 `CreateUserRequest`，仅包含创建用户允许的字段。
- Controller 将请求 DTO 映射为 `User` 后再调用现有 Service。
- 保持成功响应结构和既有 CRUD 行为不变。

## 学习点

DTO 与 Entity 的职责边界、Spring MVC 的 `@RequestBody`、手动映射为何比一开始引入 MapStruct 更适合当前规模。

## 验收

1. 请求带 `id` 或 `deleted` 时不会影响数据库主键和逻辑删除字段。
2. 合法创建请求仍返回新用户 ID。
3. 至少一条测试覆盖「请求字段不会越权写入实体」。

## 不做

不添加校验注解、不处理异常、不修改更新接口、不引入 MapStruct。这些分别留给后续卡片。

## 验证

```bash
cd boot-server
./mvnw -pl services/user-service -am test
```

## 完成记录

日期：2026-09-09

提交：待提交

测试与接口证据：

- `cd boot-server && ./mvnw -pl services/user-service -am test`：6 个测试通过。
- `UserControllerWebTests`：`POST /api/users` 传入 `id=999`、`deleted=1` 时，Service 接收到的 `User` 中这两个字段仍为 `null`，合法字段正常保存并返回新 ID。
