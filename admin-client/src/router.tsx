import { createBrowserRouter, Navigate } from "react-router-dom"

import { AdminLayout } from "@/layouts/AdminLayout"
import { LoginPage } from "@/pages/Login"
import { NotFoundPage } from "@/pages/NotFound"
import { UsersPage } from "@/pages/Users"

/**
 * 路由集中定义，后续认证守卫会在此处包裹管理布局，避免页面内部自行判断 URL 或跳转。
 */
export const appRouter = createBrowserRouter([
  {
    path: "/",
    element: <Navigate to="/users" replace />,
  },
  {
    path: "/login",
    element: <LoginPage />,
  },
  {
    element: <AdminLayout />,
    children: [
      {
        path: "/users",
        element: <UsersPage />,
      },
    ],
  },
  {
    path: "*",
    element: <NotFoundPage />,
  },
])
