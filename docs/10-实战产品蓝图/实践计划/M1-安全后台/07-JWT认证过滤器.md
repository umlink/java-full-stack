# M1-07：JWT 认证过滤器

状态：**已完成**  前置：M1-06 已完成

返回 [M1 任务列表](README.md)。

## 目标

让受保护接口能够从请求中的 Bearer token 恢复当前登录用户；缺失、篡改或过期 token 一律返回 401。

## 范围

- 配置 Spring Security 的最小 Security Filter Chain。
- 从 `Authorization: Bearer <token>` 提取并校验 JWT。
- 校验成功后写入 SecurityContext。
- 定义公开接口与受保护接口的最小白名单。

## 学习点

Filter Chain 顺序、SecurityContext、认证与授权的区别、401 的语义。

## 验收

1. 不带 token 访问受保护接口返回 401。
2. 篡改或过期 token 返回 401。
3. 有效 token 可以访问已认证但未限制角色的接口。

## 不做

不做角色校验、刷新 token、黑名单、OAuth2 登录；这些不是认证过滤器的职责。

## 完成记录

日期：2026-09-09

提交：待提交

测试与接口证据：

- `./mvnw -pl services/user-service -am test`：37 个测试通过。
- `GET /api/users/me`：不带 token、篡改 token、过期 token、非法主体 ID 均返回 `401/40100`；使用登录返回的 token 时返回对应用户 ID。
- `POST /api/auth/register`、`POST /api/auth/login` 保持公开；其余接口默认需要认证。

## 实现要点

- `JwtAuthenticationFilter` 从 `Authorization: Bearer <token>` 提取令牌，验签、检查过期时间后将用户 ID 写入 `SecurityContext`。
- `SecurityConfig` 使用无状态会话；认证与授权分离，当前只要求已登录，不在本卡加载角色或判断权限。
- `RestAuthenticationEntryPoint` 处理过滤器链中的认证失败，确保它与 Controller 的异常响应一样使用 `Result` 和 `ErrorCode.UNAUTHORIZED`。

## 学习记录

- 对外地址为 `/api/auth/**`，但 `spring.mvc.servlet.path=/api` 已由 DispatcherServlet 消费；Security 的白名单匹配内部路径 `/auth/**`。
- `SecurityContext` 是当前请求线程的身份容器；`@AuthenticationPrincipal Long userId` 可在 Controller 中读取过滤器设置的用户 ID。
