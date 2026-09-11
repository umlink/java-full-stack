/**
 * 环境变量的唯一读取入口（前端开发规范第 6 节）。
 *
 * Vite 只把 `VITE_` 前缀的变量暴露给浏览器代码，这里集中读取 `VITE_API_BASE_URL`，
 * 页面与请求层禁止再碰 `import.meta.env`，换环境（联调 / 生产）时只改 `.env` 不改代码。
 */
const rawBaseUrl = import.meta.env.VITE_API_BASE_URL

// 缺配置时立即失败：与其发出错误地址的请求再排查，不如启动时就指出怎么修
if (!rawBaseUrl) {
  throw new Error(
    "缺少环境变量 VITE_API_BASE_URL：请在 admin-client 目录复制 .env.example 为 .env 并按需修改"
  )
}

/** 去掉结尾斜杠，避免 `/api/` + `/auth/login` 拼出双斜杠路径 */
export const apiBaseUrl = rawBaseUrl.replace(/\/+$/, "")
