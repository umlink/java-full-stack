/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * 后端 API 基础地址，只允许在 lib/env.ts 读取（前端开发规范第 6 节）。
   * 开发环境为 `/api`，由 Vite dev server 同源代理转发到本地 8080，避免跨域。
   */
  readonly VITE_API_BASE_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
