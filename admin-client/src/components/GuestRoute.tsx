import { Navigate, Outlet } from "react-router-dom"

import { getToken } from "@/lib/authSession"

/** 已登录用户无需再次停留在登录页，直接回到默认管理入口。 */
export function GuestRoute() {
  return getToken() ? <Navigate replace to="/users" /> : <Outlet />
}
