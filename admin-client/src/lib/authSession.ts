/**
 * JWT 会话封装 —— 前端开发规范第 7 节：token 只存 sessionStorage，读写必须封装。
 *
 * 为什么是 sessionStorage 而不是 localStorage：令牌只在当前标签页会话内有效，
 * 关闭标签即销毁，避免令牌在浏览器里比用户会话活得更久（M1-C-02 验收第 3 条）。
 * 页面组件禁止直接触碰 `sessionStorage`，统一经过本模块，后续换存储策略只改一处。
 */

/** 存储键不导出：调用方只能通过下方函数访问，避免散落的裸读写 */
const TOKEN_KEY = "bootmall.admin.accessToken"

/** 登录成功后写入访问令牌；覆盖写，同一会话重复登录时始终是最新令牌 */
export function saveToken(accessToken: string): void {
  sessionStorage.setItem(TOKEN_KEY, accessToken)
}

/** 读取当前会话的访问令牌；未登录或会话已销毁时返回 null */
export function getToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY)
}

/** 登出或令牌失效时清除；M1-C-03 的退出登录会复用本函数 */
export function clearToken(): void {
  sessionStorage.removeItem(TOKEN_KEY)
}
