import { createBrowserRouter, Navigate } from "react-router-dom"

import { AdminLayout } from "@/components/layout/admin-layout"
import { LoginPage } from "@/features/auth/pages/login-page"
import { UsersPage } from "@/features/users/pages/users-page"
import { NotFoundPage } from "@/pages/not-found-page"

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
