import type { UserSummary } from "@/api/modules/type"
import { request } from "@/lib/request"

/**
 * 用户管理业务模块（对齐后端 /users 资源）。
 *
 * 受 user:manage 权限保护：USER 令牌请求得到 403/40300，由页面转入无权限状态；
 * 会话失效的 401/40100 由统一客户端清理会话并跳登录，不进入页面分支。
 */

/** 用户列表：GET /users，需要 user:manage 权限，按用户 ID 升序 */
export function listUsers(): Promise<UserSummary[]> {
  return request<UserSummary[]>("/users")
}
