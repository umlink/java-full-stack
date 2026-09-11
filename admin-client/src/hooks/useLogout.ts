import { useNavigate } from "react-router-dom"

import { clearToken } from "@/lib/authSession"

/**
 * 退出登录的唯一出口：清空会话并回到登录页。
 *
 * 管理布局侧栏与无权限状态共用；将来「登出必须清 token 和用户缓存」的
 * 缓存清理也集中在这里，避免多处退出各自维护一份清理清单。
 */
export function useLogout() {
  const navigate = useNavigate()

  return () => {
    clearToken()
    navigate("/login", { replace: true })
  }
}
