import { useEffect, useState } from "react"

import { listUsers } from "@/api/modules/users"
import { API_CODE_FORBIDDEN, ApiError } from "@/lib/request"

/** 用户目录页的请求状态：本卡到「就绪」为止，空列表与数据渲染由 M1-C-06 接入 */
type UsersPageState = "loading" | "ready" | "forbidden" | "error"

/**
 * 用户目录页的私有编排（M1-C-04）：进入页面即请求 `GET /users`，
 * 按后端授权结果驱动页面状态。
 *
 * - `403/40300` → `forbidden`：会话保留，页面转入无权限状态；
 * - `401/40100`（会话失效）由统一客户端清理会话并跳登录，不进入页面分支；
 * - 其余受控失败 → `error`：只透传统一文案，不追加内部细节。
 */
export function useUsersService() {
  const [state, setState] = useState<UsersPageState>("loading")
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    // 组件卸载或重复执行后不再落状态，避免旧请求覆盖新结果
    let cancelled = false

    async function load() {
      try {
        await listUsers()
        if (!cancelled) {
          setState("ready")
        }
      } catch (error) {
        if (cancelled) {
          return
        }
        if (error instanceof ApiError && error.code === API_CODE_FORBIDDEN) {
          setState("forbidden")
          return
        }
        setState("error")
        setErrorMessage(error instanceof ApiError ? error.message : "加载失败，请稍后重试")
      }
    }

    void load()
    return () => {
      cancelled = true
    }
  }, [])

  return { state, errorMessage }
}
