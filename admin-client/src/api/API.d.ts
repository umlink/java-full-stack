/**
 * API 层统一类型出口：页面统一从 `@/api/API` 引入各业务模块的请求 / 响应类型，
 * 不直接深入 modules 子目录。新增业务模块时，在此追加对应的 re-export。
 */
export type {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
} from "@/api/modules/type"
