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

/**
 * 用户条目：对齐后端 User 实体的对外 JSON 字段（passwordHash 已被服务端 @JsonIgnore 排除）。
 *
 * 除 id 外均可空：M0 旧数据与未填写资料存在 null 字段，渲染方（M1-C-06）自行决定空值展示。
 * createTime / updateTime 为 ISO 8601 字符串（后端 LocalDateTime 序列化产物）。
 */
export interface UserSummary {
  id: number
  username: string | null
  name: string | null
  email: string | null
  status: number | null
  age: number | null
  deleted: number | null
  createTime: string | null
  updateTime: string | null
}
