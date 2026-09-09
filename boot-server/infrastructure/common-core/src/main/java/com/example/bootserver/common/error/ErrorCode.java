package com.example.bootserver.common.error;

/**
 * 面向客户端的稳定错误分类。
 * <p>
 * HTTP 状态码描述本次通信结果，业务码供客户端确定错误处理方式。公共模块仅保存数值，
 * 不依赖 Spring Web，因此也可被未来的非 HTTP 入口复用。
 * <p>
 * 业务码一旦被前端或外部调用方消费就应保持稳定。新增错误时优先扩展本枚举，避免在
 * Controller 中散落魔法数字，造成同类错误在不同接口上表现不一致。
 */
public enum ErrorCode {

    /** 请求可达但字段、格式或请求体不满足接口契约。 */
    PARAMETER_ERROR(400, 40000, "请求参数不合法"),
    /** 当前请求未携带有效身份凭据。 */
    UNAUTHORIZED(401, 40100, "请先登录"),
    /** 身份已确认，但角色或权限不足。 */
    FORBIDDEN(403, 40300, "没有访问权限"),
    /** 目标资源不存在或已被逻辑删除。 */
    NOT_FOUND(404, 40400, "资源不存在"),
    /** 请求与资源当前状态冲突，例如重复注册。 */
    CONFLICT(409, 40900, "请求与当前资源状态冲突"),
    /** 未预期的服务端故障，消息不能携带内部实现细节。 */
    SYSTEM_ERROR(500, 50000, "系统繁忙，请稍后再试");

    private final int httpStatus;
    private final int code;
    private final String message;

    ErrorCode(int httpStatus, int code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
