# M1-09：OpenAPI 与 M1 回归

返回 [实践计划与进度](../README.md)。

## 目标

将 M1 的接口契约对外可见，并将 M0 与 M1 的关键行为收拢为稳定回归验证，作为进入 M2 的唯一门槛。

## 范围

- 引入并配置 SpringDoc OpenAPI。
- 为注册、登录和管理接口补充必要描述。
- 补齐 M1 关键分支测试：校验失败、404、重复注册、登录失败、401、403、admin 成功。
- 运行全量 Maven 测试并记录接口访问证据。

## 学习点

OpenAPI 契约、集成测试覆盖范围、回归门禁、可复现验收。

## 验收

1. OpenAPI 页面可访问，接口模型与实际响应一致。
2. `./mvnw -pl services/user-service -am test` 全绿。
3. M1 DoD 中的 401、403、admin 成功都有测试和接口记录。
4. 在本卡片的「完成记录」段填写完成日期、提交和验证证据。

## 不做

不开始商品、订单、Redis、MQ 或微服务任务；M2 在本卡片验收后才解锁。

## 完成记录

日期：2026-09-09

提交：`f51dfa5`（feat: 完成M1接口文档与回归验收）

测试与接口证据：`./mvnw -pl services/user-service -am test`：42 个测试通过。真实 HTTP 集成测试验证 `GET /api/v3/api-docs` 返回 200 且包含 `/auth/register`、`/users`、`bearerAuth`，`GET /api/swagger-ui/index.html` 返回 200；M1-08 已覆盖管理接口无 token 的 401/40100、USER 的 403/40300 和 ADMIN 的 200/0。
