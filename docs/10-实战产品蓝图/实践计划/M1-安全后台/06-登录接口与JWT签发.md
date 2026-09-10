# M1-06：登录接口与 JWT 签发

返回 [实践计划与进度](../README.md)。

## 目标

用户使用正确凭据登录后获得短期有效的 JWT；失败时不透露用户名是否存在。

## 范围

- 新增登录请求和响应 DTO。
- 校验密码哈希并签发包含用户标识的 JWT。
- 令牌过期时间由配置提供，不写死在业务代码。
- 错误用户名和错误密码返回相同失败语义。

## 学习点

JWT 的 header、payload、signature；认证失败信息泄露；配置与密钥管理的边界。

## 验收

1. 正确凭据能获得可解析、含过期时间的 token。
2. 错误凭据返回统一认证失败，不泄露账号存在性。
3. 过期时间可通过配置调整，测试覆盖成功与失败分支。

## 不做

不支持刷新 token、黑名单、单点登录、多端设备管理；真实密钥不提交到仓库。

## 完成记录

日期：2026-09-09

提交：待提交

测试与接口证据：

- `cd boot-server && ./mvnw clean test -pl services/user-service -am`：35 个测试通过；使用 clean 构建验证了测试专用假密钥不会覆盖主配置。
- Web 层覆盖：`POST /api/auth/login` 正确凭据返回 `accessToken` 和 `expiresAt`；错误凭据统一返回 HTTP 401 / 40100 / “用户名或密码错误”，不签发 token。
- 集成覆盖：真实 `/api/auth/login` 可访问而 `/auth/login` 为 404；签发 token 经同一 HS256 密钥验签后含用户 ID、`iat` 与配置为 `PT5M` 的 `exp`；未知用户名和错误密码的业务错误码与提示完全一致。
- 运行时密钥只从 `JWT_SECRET_BASE64` 读取；测试使用 `src/test/resources/application.properties` 中的固定假密钥，真实密钥不提交。
