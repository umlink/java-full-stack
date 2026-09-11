/** 后端 `Result.code` 的稳定契约；业务代码不在页面或接口模块中重复书写数字。 */
export const API_RESULT_CODE = {
  SUCCESS: 0,
  UNAUTHORIZED: 40100,
  FORBIDDEN: 40300,
} as const
