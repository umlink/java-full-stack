import { useCallback, useEffect, useState } from "react"

import type { UserSummary } from "@/api/API"
import { listUsers } from "@/api/modules/users"
import { API_RESULT_CODE } from "@/lib/apiContract"
import { ApiError } from "@/lib/request"

/** 用户目录页的请求状态：加载、就绪、空、失败、无权限，覆盖管理页全部分支 */
type UsersPageState = "loading" | "ready" | "empty" | "error" | "forbidden"

/**
 * 用户目录页的表格行模型（M1-C-06）：由后端 `UserSummary` 映射而来。
 *
 * 后端字段可空且为业务码（status 为 0/1 数字），展示层的兜底文案与
 * 状态语义（正常/已停用/未知）在此集中定义，页面不直接消费 DTO。
 */
export interface UserRow {
  id: number
  username: string
  name: string
  email: string
  /** 状态展示文案：1 → 正常，0 → 已停用，null/其他 → 未知 */
  statusLabel: string
  /** 状态对应的 Badge 色调，与 statusLabel 一一对应 */
  statusTone: "secondary" | "destructive" | "outline"
  /** 创建时间，zh-CN 本地化文案；null → "—" */
  createTime: string
}

/** 加载结果提交回调：把取数后的分类结果一次性落为页面状态 */
type Commit = (rows: UserRow[], state: UsersPageState, errorMessage: string | null) => void

/** 空值统一兜底：列表字段可空（M0 旧数据与未填写资料），表格不允许出现裸 null */
const MISSING = "—"

/** 创建时间格式化器：中日期 + 短时间（如 2026年9月11日 14:30），模块级复用 */
const dateTimeFormatter = new Intl.DateTimeFormat("zh-CN", {
  dateStyle: "medium",
  timeStyle: "short",
})

/** 状态码 → 展示文案与色调；未识别的码值（含 null）按「未知」处理，不猜测语义 */
function toStatus(status: number | null): Pick<UserRow, "statusLabel" | "statusTone"> {
  switch (status) {
    case 1:
      return { statusLabel: "正常", statusTone: "secondary" }
    case 0:
      return { statusLabel: "已停用", statusTone: "destructive" }
    default:
      return { statusLabel: "未知", statusTone: "outline" }
  }
}

/** 后端条目 → 页面行模型；空字符串时间按缺失处理，避免落入无效日期 */
function toUserRow(user: UserSummary): UserRow {
  const { statusLabel, statusTone } = toStatus(user.status)
  return {
    id: user.id,
    username: user.username ?? MISSING,
    name: user.name ?? MISSING,
    email: user.email ?? MISSING,
    statusLabel,
    statusTone,
    createTime: user.createTime ? dateTimeFormatter.format(new Date(user.createTime)) : MISSING,
  }
}

/**
 * 加载入口：取数并把结果按规则分类后交给 `commit` 落状态——初始加载与手动刷新共用，
 * 不在两处复制取数与状态机逻辑。放在模块层（而非组件内）是为了让 effect 只调用
 * 普通函数、不直接触达 setState：effect 同步阶段调用会触发 set-state-in-effect 规则
 * （初始加载属于异步订阅，setState 只应发生在 await 之后的回调里）。
 *
 * - 成功且空数组 → `empty`，成功非空 → `ready`；
 * - `403/40300` → `forbidden`：会话保留，页面转入无权限状态；
 * - `401/40100`（会话失效）由统一客户端清理会话并跳登录，不进入页面分支；
 * - 其余受控失败 → `error`：只透传统一文案，不追加内部细节。
 *
 * `isCancelled` 由调用方决定是否放弃提交：初始加载绑定卸载守卫，手动刷新不设守卫
 * （组件卸载后 setState 在 React 18+ 为空操作，无旧请求覆盖新结果的风险）。
 */
async function loadUsers(isCancelled: () => boolean, commit: Commit) {
  try {
    const list = await listUsers()
    if (isCancelled()) {
      return
    }
    commit(list.map(toUserRow), list.length === 0 ? "empty" : "ready", null)
  } catch (error) {
    if (isCancelled()) {
      return
    }
    if (error instanceof ApiError && error.code === API_RESULT_CODE.FORBIDDEN) {
      commit([], "forbidden", null)
      return
    }
    commit([], "error", error instanceof ApiError ? error.message : "加载失败，请稍后重试")
  }
}

/**
 * 用户目录页的私有编排（M1-C-04 起，M1-C-06 扩展）：进入页面即请求 `GET /users`，
 * 按后端授权结果驱动页面状态；手动刷新与初始加载共用 `loadUsers` 同一加载入口。
 */
export function useUsersService() {
  const [state, setState] = useState<UsersPageState>("loading")
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [rows, setRows] = useState<UserRow[]>([])
  const [isRefreshing, setIsRefreshing] = useState(false)

  const commit = useCallback<Commit>((nextRows, nextState, message) => {
    setRows(nextRows)
    setState(nextState)
    setErrorMessage(message)
  }, [])

  useEffect(() => {
    // 组件卸载或重复执行后不再落状态，避免旧请求覆盖新结果
    let cancelled = false
    void loadUsers(() => cancelled, commit)
    return () => {
      cancelled = true
    }
  }, [commit])

  async function refresh() {
    setIsRefreshing(true)
    try {
      await loadUsers(() => false, commit)
    } finally {
      setIsRefreshing(false)
    }
  }

  return { state, errorMessage, rows, isRefreshing, refresh }
}
