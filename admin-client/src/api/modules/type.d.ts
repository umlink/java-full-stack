/**
 * 登录业务模块的类型定义，对齐后端 AuthController 的 DTO 白名单契约。
 *
 * 模块内部的请求函数从本文件取类型；页面统一从 `@/api/API` 统一出口引入，
 * 不直接深入 modules 子目录。字段名与后端 DTO 一一对应，禁止前端自行增删字段。
 */

/** 登录请求：对齐后端 LoginRequest——只接受用户名和密码，多余字段会被服务端丢弃 */
export interface LoginRequest {
  username: string
  password: string
}

/**
 * 登录响应：对齐后端 LoginResponse——短期访问令牌与过期时间。
 * expiresAt 为 ISO 8601 字符串（后端 Instant 序列化产物），不含密码哈希等敏感数据。
 */
export interface LoginResponse {
  accessToken: string
  expiresAt: string
}

/** 注册请求：对齐后端 RegisterRequest——角色由服务端固定为 USER，客户端不可指定 */
export interface RegisterRequest {
  username: string
  email: string
  password: string
}
