import { useState } from "react"
import { useLocation, useNavigate } from "react-router-dom"

import type { LoginRequest } from "@/api/API"
import { login } from "@/api/modules/login"
import { saveToken } from "@/lib/authSession"
import { ApiError } from "@/lib/request"

/** 表单字段名 → 受控校验文案 */
type LoginFieldErrors = Partial<Record<"username" | "password", string>>
const DEFAULT_RETURN_PATH = "/users"

type LoginLocationState = {
  from?: unknown
}

/** 只允许返回当前卡片的用户管理路由，避免把任意路由 state 当作登录跳转目标。 */
function getReturnPath(state: unknown): string {
  const returnPath =
    typeof state === "object" && state !== null
      ? (state as LoginLocationState).from
      : undefined

  if (
    typeof returnPath === "string" &&
    (returnPath === DEFAULT_RETURN_PATH ||
      returnPath.startsWith(`${DEFAULT_RETURN_PATH}?`) ||
      returnPath.startsWith(`${DEFAULT_RETURN_PATH}#`))
  ) {
    return returnPath
  }

  return DEFAULT_RETURN_PATH
}

/**
 * 登录页的私有编排（M1-C-02）：字段状态、提交前校验、调接口、写会话、跳转。
 *
 * 成功路径：`login()` 返回已解包的数据，令牌写入 sessionStorage 后跳转 `/users`；
 * 失败路径：只展示后端统一受控文案，令牌不进日志、不进错误提示。
 */
export function useLoginService() {
  const location = useLocation()
  const navigate = useNavigate()
  const [username, setUsername] = useState("")
  const [password, setPassword] = useState("")
  const [fieldErrors, setFieldErrors] = useState<LoginFieldErrors>({})
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  function handleUsernameChange(value: string) {
    setUsername(value)
    // 输入即清掉对应字段的旧校验与上一次提交的失败提示，避免错误文案滞留
    setFieldErrors((prev) => ({ ...prev, username: undefined }))
    setErrorMessage(null)
  }

  function handlePasswordChange(value: string) {
    setPassword(value)
    setFieldErrors((prev) => ({ ...prev, password: undefined }))
    setErrorMessage(null)
  }

  async function submit(params: LoginRequest) {
    // 前端校验与后端 @NotBlank 同契约（非空），拦住明显空提交再发请求
    const errors: LoginFieldErrors = {}
    if (!params.username.trim()) {
      errors.username = "请输入用户名"
    }
    if (!params.password) {
      errors.password = "请输入密码"
    }
    setFieldErrors(errors)
    if (errors.username || errors.password) {
      return
    }

    setIsSubmitting(true)
    setErrorMessage(null)
    try {
      const data = await login({ username: params.username.trim(), password: params.password })
      saveToken(data.accessToken)
      navigate(getReturnPath(location.state), { replace: true })
    } catch (error) {
      // 后端对「用户不存在」与「密码错误」统一返回「用户名或密码错误」，防止账号枚举；
      // 此处仅透传受控文案，不追加任何内部细节
      setErrorMessage(
        error instanceof ApiError ? error.message : "登录失败，请稍后重试",
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  return {
    username,
    password,
    handleUsernameChange,
    handlePasswordChange,
    fieldErrors,
    errorMessage,
    isSubmitting,
    submit,
  }
}
