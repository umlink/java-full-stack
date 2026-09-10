# admin-client/AGENTS.md

本文件定义 `admin-client/` B 端管理后台开发规则。

## 1. 技术栈

- React 19。
- TypeScript。
- Vite。
- React Router。
- Tailwind CSS 4。
- shadcn/ui。
- Base UI。
- Lucide。

完整前端规则见 [前端开发规范](../docs/10-实战产品蓝图/前端开发规范.md)。本文件只记录 B 端项目级约束。

## 2. 当前边界

- 当前主线：M1-C 管理后台接入。
- 当前待开始卡片：`M1-C-01：统一 HTTP 客户端与 Result 契约`。
- 后端接口文档：`http://localhost:8080/api/swagger-ui/index.html`。
- API 基础地址只从 `VITE_API_BASE_URL` 读取。
- 不创建 Mock 业务数据替代后端契约。
- 不提前实现注册页、角色管理、商品、订单、C 端页面。

## 3. 目录规则

```text
src/
  App.tsx
  main.tsx
  router.tsx
  assets/
  components/
    ui/
  hooks/
  layouts/
  lib/
  pages/
  providers/
```

- 页面固定为 `pages/<PageName>/index.tsx`。
- 页面目录使用 PascalCase。
- 自有 React 组件文件使用 PascalCase。
- Hook 文件使用 `useXxx.ts`。
- 页面 service Hook 使用 `useXxxService.ts`。
- `components/ui` 保留 shadcn 小写命名。
- 禁止新增 `src/app/`、`src/features/`、`components/layout/`。

## 4. B 端 UI

- 管理页面优先紧凑、稳定、可扫描。
- 必须覆盖加载、空、错误、无权限状态。
- 表格、筛选、分页、操作反馈是管理页默认能力。
- 图标使用 Lucide。
- 图标按钮必须有 `aria-label`。
- shadcn/ui 可表达的控件禁止手写替代。
- 禁止营销式 hero、装饰性大卡片、卡片嵌套卡片。

## 5. HTTP 与认证

- 统一 HTTP 客户端放 `src/lib/`。
- 页面和 service Hook 禁止直接解析 `Result<T>`。
- JWT 只存 `sessionStorage`。
- token 读写必须封装。
- `401/40100` 清 token 并跳登录。
- `403/40300` 保留会话并显示无权限。
- 路由守卫只判断登录状态，权限以后端结果为准。

## 6. 验证

前端变更必须运行：

```bash
pnpm typecheck
pnpm lint
pnpm build
git diff --check
```

涉及路由、登录、权限、布局时，必须浏览器验证对应路径和移动端布局。
