# admin-client

BootMall B 端管理后台，技术栈为 React 19、TypeScript、Vite、Tailwind CSS 4、shadcn/ui 与 Lucide。

## 当前进度

后端 M1 的注册、登录、JWT、RBAC 与 OpenAPI 已完成。`00-管理后台路由与应用壳` 已完成；当前待开始的是 [M1-C 管理后台接入](../docs/10-实战产品蓝图/实践计划/M1-管理后台接入/README.md) 的 `01-统一 HTTP 客户端与 Result 契约`，必须按卡片顺序实施。

已有后端接口文档：`http://localhost:8080/api/swagger-ui/index.html`。开发时通过 `VITE_API_BASE_URL` 指向 `http://localhost:8080/api`，不要在页面中硬编码地址。

## 命令

```bash
npm install
npm run dev
npm run typecheck
npm run lint
npm run build
```

前端目录、请求、认证与验收规范见仓库根目录 [AGENTS.md](../AGENTS.md)。
