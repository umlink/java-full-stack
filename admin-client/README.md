# admin-client

BootMall B 端管理后台，技术栈为 React 19、TypeScript、Vite、Tailwind CSS 4、shadcn/ui 与 Lucide。

## 开发进度

本工程当前推进 M1-C 管理后台接入；进度与当前卡片以 [实践计划与进度](../docs/10-实战产品蓝图/实践计划/README.md) 为准，必须按卡片顺序实施。

已有后端接口文档：`http://localhost:8080/api/swagger-ui/index.html`。开发时 `VITE_API_BASE_URL=/api`（复制 `.env.example` 为 `.env`），由 Vite dev server 同源代理转发到本地后端；本机 8080 被占用时用 `BACKEND_ORIGIN=http://localhost:8082 pnpm dev` 覆盖代理目标。不要在页面中硬编码地址。

## 目录结构

```text
src/
  api/         业务接口定义：modules/<模块>.ts + type.d.ts，API.d.ts 统一类型出口
  pages/       路由页面，每个页面独立目录
  layouts/     管理后台等可复用页面框架
  providers/   全局 Provider 组合
  components/  shadcn/ui 等跨页面基础组件
  hooks/       跨页面 React Hook
  lib/         HTTP 客户端、鉴权、环境变量、工具函数
```

页面组织约定：

```text
pages/
  Users/
    index.tsx
    components/
    useUsersService.ts
```

`components/ui` 保持为 shadcn 组件落点；路由页面以 `pages/<PageName>/index.tsx` 暴露；页面私有组件放入当前页面的 `components/`；页面私有请求与状态编排需要时再新增 `useXxxService.ts`。自有 React 组件文件使用 PascalCase，例如 `layouts/AdminLayout.tsx`、`providers/AppProvider.tsx`；Hook 文件使用 `useXxx.ts`。页面框架放入 `layouts/`；全局组合逻辑放入 `providers/`，避免 `main.tsx` 和页面组件承担全局装配职责。

## 命令

```bash
pnpm install
pnpm dev
pnpm typecheck
pnpm lint
pnpm build
```

项目级开发规则见 [admin-client/AGENTS.md](AGENTS.md)；通用前端规范见 [前端开发规范](../docs/10-实战产品蓝图/前端开发规范.md)。
