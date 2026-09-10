import path from "path"
import tailwindcss from "@tailwindcss/vite"
import react from "@vitejs/plugin-react"
import { defineConfig } from "vite"

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    proxy: {
      // 后端未开启 CORS，开发期由 dev server 把 /api/** 同源转发到本地后端，
      // 浏览器始终只请求自己（5173），不存在跨域；地址本身仍从 VITE_API_BASE_URL 读取。
      // 默认 8080；本机端口被占时用 BACKEND_ORIGIN=http://localhost:8082 pnpm dev 覆盖
      "/api": {
        target: process.env.BACKEND_ORIGIN ?? "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
})
