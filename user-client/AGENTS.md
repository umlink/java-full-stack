# user-client/AGENTS.md

本文件定义 `user-client/` C 端商城开发规则。

## 1. 技术栈

- React 19。
- TypeScript。
- Vite。
- React Router。
- Tailwind CSS 4。
- shadcn/ui。
- Base UI。
- Lucide。

完整前端规则见 [前端开发规范](../docs/10-实战产品蓝图/前端开发规范.md)。本文件只记录 C 端项目级约束。

## 2. 当前边界

- 当前项目只完成初始化。
- 商品、购物车、下单依赖 M2 交易核心后端接口。
- M2 未拆分完成前，不创建 Mock 业务、页面或接口调用。
- 业务页面必须等对应后端卡片达到 DoD 后再创建。

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

## 4. C 端 UI

- 商城页面优先清晰、顺畅、可转化。
- 必须覆盖加载、空、错误状态。
- 商品列表、详情、购物车、下单流程必须有明确状态边界。
- 表单字段必须有 Label 或等价可访问名称。
- 图标使用 Lucide。
- shadcn/ui 可表达的控件禁止手写替代。

## 5. HTTP 与认证

- 统一 HTTP 客户端放 `src/lib/`。
- 页面和 service Hook 禁止直接解析 `Result<T>`。
- 登录态存储必须封装。
- token 禁止进入 URL、日志、错误提示、localStorage。
- 权限和身份最终以后端响应为准。

## 6. 验证

前端变更必须运行：

```bash
pnpm typecheck
pnpm lint
pnpm build
git diff --check
```

涉及路由、登录、购物车、下单、布局时，必须浏览器验证关键路径和移动端布局。
