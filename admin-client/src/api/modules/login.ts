import type {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
} from "@/api/modules/type"
import { request } from "@/lib/request"

/**
 * 登录业务模块：认证相关的公开接口统一在此封装（对齐后端 /auth 资源）。
 *
 * 页面与 Hook 只调用本模块导出的函数，不直接拼路径、不碰 fetch 细节；
 * 成功返回已解包的业务数据，网络失败与服务端失败统一折叠为 ApiError 抛出。
 */

/** 登录：POST /auth/login，成功返回短期访问令牌与过期时间 */
export function login(params: LoginRequest): Promise<LoginResponse> {
  return request<LoginResponse>("/auth/login", {
    method: "POST",
    body: JSON.stringify(params),
  })
}

/** 注册：POST /auth/register，成功返回新用户 ID（服务端默认绑定 USER 角色） */
export function register(params: RegisterRequest): Promise<number> {
  return request<number>("/auth/register", {
    method: "POST",
    body: JSON.stringify(params),
  })
}
