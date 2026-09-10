# M1-C-01：统一 HTTP 客户端与 Result 契约

状态：**已完成**  前置：M1-C-00 已完成

返回 [管理后台任务列表](README.md)。

## 目标

建立唯一的前端 HTTP 入口，使页面可以获得已解包的 `Result<T>` 数据和稳定的失败分类。

## 范围

- 从 `VITE_API_BASE_URL` 读取服务端基础地址。
- 定义 `Result<T>`、受控 API 错误类型和 `request` 客户端。
- 处理 JSON、网络失败、非 2xx 状态与服务端业务码；暂不加入 JWT Header。

## 学习点

环境变量、泛型响应契约、Fetch 边界、传输错误与业务错误的区分。

## 验收

1. 使用现有公开登录接口进行一次成功请求验证。
2. 无法连接与服务端失败能转换为页面可展示的受控错误，页面不自行解析 `Result.code`。
3. 不在页面组件硬编码 `localhost`、端口或 `/api`。
4. `npm run typecheck`、`npm run lint`、`npm run build` 通过。

## 不做

不创建登录表单，不保存 Token，不处理 401 或 403 跳转。

## 完成记录

日期：2026-09-10

提交：待用户确认后提交

测试与页面证据：

- `pnpm typecheck`、`pnpm lint`、`pnpm build` 全部通过。
- 经 Vite dev server 同源代理（`/api` → 本地后端）真实调用公开接口：`POST /auth/register` 返回 `Result<Long>`（`data` 解包验证）；`POST /auth/login` 成功并解包出 `accessToken` 与 `expiresAt`。
- 错误凭据登录返回 HTTP 401 + `code:40100`「用户名或密码错误」，折叠为 `business` 类 `ApiError`；后端停机时代理返回 502，折叠为 `http` 类「服务暂时不可用，请稍后重试」；均为页面可直接展示的受控文案。
- 实现物：`lib/env.ts`（`VITE_API_BASE_URL` 唯一读取入口）、`lib/request.ts`（`Result<T>` + `ApiError` 三分类 + `request<T>()`）、`api/modules/login.ts` + `type.d.ts`、`api/API.d.ts` 统一类型出口；登录页「检查后端连接」探针为临时验证载体，M1-C-02 由登录表单取代。
- 页面级浏览器确认与 M1-C-02 的登录页浏览器验证合并进行。
