import { apiBaseUrl } from "@/lib/env"

/**
 * 后端统一响应体，对齐 boot-server `common-core` 的 `Result<T>` 契约。
 *
 * 分层约定与后端一致：HTTP 状态码描述「通信是否正常」，业务 code 描述「业务是否成功」。
 * code = 0 表示成功；失败码由后端 `ErrorCode` 枚举分段（如 40100 未认证、40300 无权限）。
 */
export type Result<T> = {
  code: number
  message: string
  data: T
}

/**
 * 受控错误的来源分类，页面据此决定展示方式：
 *
 * - `network`：请求没有到达服务器（断网、后端未启动、跨域被拦）；
 * - `business`：服务器返回了受控的 `Result` 业务失败（如登录密码错误 40100）；
 * - `http`：响应既非成功也不是可解析的 `Result`（如网关 502 的 HTML 错误页）。
 */
export type ApiErrorKind = "network" | "business" | "http"

/**
 * 唯一向前端抛出的请求错误：携带稳定的分类和业务码，页面只消费 `message` 展示，
 * 不再自行解析 `Result.code`。后续卡片按 `kind` / `code` 做 401 跳登录等分支。
 */
export class ApiError extends Error {
  readonly kind: ApiErrorKind
  /** 业务码（对齐后端 ErrorCode，如 40100）；响应中没有业务码时为 0 */
  readonly code: number
  /** HTTP 状态码；请求未到达服务器（network）时为 0 */
  readonly httpStatus: number

  constructor(kind: ApiErrorKind, code: number, httpStatus: number, message: string) {
    super(message)
    this.name = "ApiError"
    this.kind = kind
    this.code = code
    this.httpStatus = httpStatus
  }
}

/**
 * 统一 HTTP 入口：把「网络失败 / 非受控 HTTP 失败 / 业务码失败」都折叠成 `ApiError`，
 * 成功时直接返回已解包的 `Result.data`，调用方拿到的就是业务数据本身。
 *
 * @param path 资源路径（如 `/auth/login`），不含主机、端口与 `/api` 前缀
 * @param init 透传给 fetch 的初始化参数，`body` 存在时自动补 JSON 请求头
 */
export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const initWithJsonHeaders =
    init?.body != null
      ? { ...init, headers: { "Content-Type": "application/json", ...init?.headers } }
      : init

  let response: Response
  try {
    response = await fetch(`${apiBaseUrl}${path}`, initWithJsonHeaders)
  } catch {
    // fetch 只在网络层失败时抛异常；HTTP 4xx/5xx 走正常返回，不会进入这里
    throw new ApiError("network", 0, 0, "无法连接服务器，请稍后重试")
  }

  // 成功与失败响应都尝试按 Result 解析：后端统一异常处理保证失败响应也带业务码
  let result: Result<T> | null = null
  try {
    result = (await response.json()) as Result<T>
  } catch {
    // 响应不是 JSON（如网关错误页）时保持 null，交给下方按 HTTP 状态归类
  }

  // 成功必须同时满足 HTTP 通信成功和业务契约成功。即使中间层意外返回了
  // `code = 0`，非 2xx 也不能让调用方把无效响应当作业务数据继续处理。
  if (result && response.ok && result.code === 0) {
    return result.data
  }

  if (result && result.code !== 0) {
    throw new ApiError("business", result.code, response.status, result.message || "请求失败")
  }

  // 拿不到 Result 体：非 2xx 多为中间层故障（如代理层 502 = 后端不可达），2xx 则是后端契约被破坏
  // 5xx 统一为对用户友好的文案，4xx 等其余状态保留码值便于排障
  const fallbackMessage =
    response.status >= 500 ? "服务暂时不可用，请稍后重试" : `请求失败（HTTP ${response.status}）`
  throw new ApiError("http", 0, response.status, fallbackMessage)
}
