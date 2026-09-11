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

- 实践顺序、解锁条件和完成状态只以 [实践计划与进度](../docs/10-实战产品蓝图/实践计划/README.md) 为准；本文件不复述具体卡片号或状态。
- 后端接口文档：`http://localhost:8080/api/swagger-ui/index.html`。
- API 基础地址只从 `VITE_API_BASE_URL` 读取。
- 不创建 Mock 业务数据替代后端契约。
- 未经实践计划明确解锁，不创建新的业务页面、接口调用或前端业务依赖。

## 3. 目录规则

```text
src/
  App.tsx
  main.tsx
  router.tsx
  api/
    modules/
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

- 管理后台面向桌面浏览器，不做移动端适配，不要求移动端布局验证。
- 管理页面优先紧凑、稳定、可扫描。
- 必须覆盖加载、空、错误、无权限状态。
- 表格、筛选、分页、操作反馈是管理页默认能力。
- 图标使用 Lucide。
- 图标按钮必须有 `aria-label`。
- shadcn/ui 可表达的控件禁止手写替代。
- 禁止营销式 hero、装饰性大卡片、卡片嵌套卡片。

## 5. HTTP 与认证

- 统一 HTTP 客户端放 `src/lib/`；接口定义按业务模块放 `src/api/modules/`，统一类型出口 `src/api/API.d.ts`。
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

涉及路由、登录、权限、布局时，必须浏览器验证对应路径。

## 7. 静态值治理

- 路由表、`Link`、`Navigate`、`navigate` 与请求错误跳转可就近保留清晰的局部路径字面量；路径在多处出现本身不构成抽常量的理由。不得用 `routes.ts` 或巨型路径常量表替代路由声明的可读性。
- 单一接口模块专用的资源路径可直接作为 `request("/auth/login")`、`request("/users")` 的局部字面量；调用方不得拼接路径，也不为此建立路径常量。即使被多个调用方使用，endpoint 仍由所属 API 模块就近声明。`VITE_*` 只从 `src/lib/env.ts` 读取；会话和主题存储键只在各自封装模块定义。
- 后端错误码放 `src/lib/` 的契约模块；协议头、MIME 与 HTTP 阈值在统一请求层就近内联；页面私有的分页、状态映射和日期格式可留在 `index.tsx` 或 `useXxxService.ts`，确有同页多文件复用时才放 `pages/<PageName>/config.ts`。
- 页面/组件表单校验的边界数值、正则和提示语必须就近保留；单个 JSX 的展示文案、Tailwind class、一次性局部计算及单测 fixture/request/assertion 也可保留字面量。只有跨模块机器契约、可部署配置、共享存储键或确有多个消费者且独立演进的策略/阈值才具名化；重复出现不自动触发抽取。禁止新增无归属的全局 `constants.ts`。
- 提交前执行 [前端开发规范](../docs/10-实战产品蓝图/前端开发规范.md) 第 10 节的 `rg` 扫描并人工审查命中；`components/ui/` 的 shadcn 生成代码不做机械改造。
