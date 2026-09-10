import { createBrowserRouter, Navigate } from "react-router-dom"

import { GuestRoute } from "@/components/GuestRoute"
import { ProtectedRoute } from "@/components/ProtectedRoute"
import { AdminLayout } from "@/layouts/AdminLayout"
import { LoginPage } from "@/pages/Login"
import { NotFoundPage } from "@/pages/NotFound"
import { UsersPage } from "@/pages/Users"

/** 路由集中定义：登录页与管理页分别由访客、认证守卫约束。 */
export const appRouter = createBrowserRouter([
  {
    path: "/",
    element: <Navigate to="/users" replace />,
  },
  {
    element: <GuestRoute />,
    children: [
      {
        path: "/login",
        element: <LoginPage />,
      },
    ],
  },
  {
    element: <ProtectedRoute />,
    children: [
      {
        element: <AdminLayout />,
        children: [
          {
            path: "/users",
            element: <UsersPage />,
          },
        ],
      },
    ],
  },
  {
    path: "*",
    element: <NotFoundPage />,
  },
])
