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

- 实践顺序、解锁条件和完成状态只以 [实践计划与进度](../docs/10-实战产品蓝图/实践计划/README.md) 为准；本文件不复述具体卡片号或状态。
- 未经计划明确解锁，不创建 Mock 业务、页面或接口调用。

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

## 7. 静态值治理

- 环境变量键、存储键、错误码、权限码和可配置业务参数必须各有唯一且语义明确的定义点；调用方只引用具名定义。路由表、单点导航目标和单一接口模块专用的 endpoint 都属于局部声明，可直接保留路径字面量；即使多个调用方使用，endpoint 仍由所属 API 模块就近声明，多处出现路径本身不构成抽常量的理由。
- 页面私有的分页、状态映射和展示格式可留在 `index.tsx` 或 `useXxxService.ts`；确有同页多文件复用时才放 `pages/<PageName>/config.ts`。跨页面也仅在确有多个消费者且需要独立演进时提升至 `lib/` 或所属 API 模块。禁止新增无归属的全局 `constants.ts`。
- 页面/组件表单校验的边界数值、正则和提示语必须就近保留；单个 JSX 的展示文案、Tailwind class、一次性局部计算及单测 fixture/request/assertion 也可保留字面量。重复出现不自动触发抽取，只有跨模块机器契约、可部署配置、共享存储键或确有多个消费者且独立演进的策略/阈值才具名化。
- 提交前执行 [前端开发规范](../docs/10-实战产品蓝图/前端开发规范.md) 第 10 节的 `rg` 扫描并人工审查命中；`components/ui/` 的 shadcn 生成代码不做机械改造。
