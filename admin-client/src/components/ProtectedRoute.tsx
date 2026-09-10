import { Navigate, Outlet, useLocation } from "react-router-dom"

import { getToken } from "@/lib/authSession"

/**
 * 管理路由的会话门槛：只依据当前标签页是否存在 JWT 决定能否进入。
 *
 * 它不在前端判断角色或权限；这类资源授权始终交给后端，并由后续的无权限状态卡片处理。
 * 未登录时把当前受保护路径写入路由 state，登录成功后可回到原目标而不把地址暴露在 URL 中。
 */
export function ProtectedRoute() {
  const location = useLocation()

  if (!getToken()) {
    return (
      <Navigate
        replace
        state={{ from: `${location.pathname}${location.search}${location.hash}` }}
        to="/login"
      />
    )
  }

  return <Outlet />
}
