# M1-C-02：登录页与 JWT 会话

返回 [实践计划与进度](../README.md)。

## 目标

让用户可在登录页提交用户名和密码，并将成功返回的 JWT 保存在当前浏览器会话中。

## 范围

- 对接 `POST /auth/login`，展示字段校验、提交中、失败和成功状态。
- 封装 `sessionStorage` 的 Token 读、写、清除能力。
- 登录成功后跳转到 `/users`；不在 URL、日志或错误提示中输出 Token。

## 学习点

受控表单、异步状态、`sessionStorage` 边界、JWT Bearer 凭据。

## 验收

1. 有效账号登录成功并写入当前会话，刷新页面后 Token 仍可读取。
2. 错误账号得到后端统一失败提示，不泄露 Token 或服务端细节。
3. 关闭浏览器标签会话后凭据消失。
4. `npm run typecheck`、`npm run lint`、`npm run build` 通过。

## 不做

不实现注册、记住我、刷新令牌、找回密码或角色判断。

## 完成记录

日期：2026-09-10

提交：`d453f82`（feat: 接入管理后台登录会话）

测试与页面证据：

- `pnpm typecheck`、`pnpm lint`、`pnpm build` 全部通过。
- 实现物：`lib/authSession.ts`（`saveToken` / `getToken` / `clearToken`，键 `bootmall.admin.accessToken`，页面不直接触碰 `sessionStorage`）、`pages/Login/index.tsx`（受控表单、字段校验、提交中、失败与成功状态）、`pages/Login/useLoginService.ts`（页面私有编排：校验 → 调接口 → 写会话 → 跳转）。
- 经 Vite dev server 同源代理真实调用后端：`POST /auth/login` 正确凭据返回 `Result<LoginResponse>`（`accessToken` + `expiresAt`）；错误密码与不存在账号**均**返回 HTTP 401 + `code:40100`「用户名或密码错误」，前端折叠为 `business` 类 `ApiError` 后只取 `message` 展示，账号枚举与服务端细节不外泄。
- headless Chrome（CDP 驱动真实浏览器）10 项检查全部通过：错误凭据不写入任何凭据、展示受控文案且页面无令牌、提交中按钮禁用并切换为「登录中…」、成功后写入会话并跳转 `/users`、URL 不含令牌、刷新后凭据仍可读取、新标签会话中无凭据（等价于关闭标签后消失）。
- 移动端 375×812 下 `/login` 与 `/users` 均无横向溢出。
