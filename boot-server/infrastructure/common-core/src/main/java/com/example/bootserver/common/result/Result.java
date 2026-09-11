package com.example.bootserver.common.result;

import com.example.bootserver.common.error.ErrorCode;

import java.util.Objects;

/**
 * 统一响应体 —— 对齐阶段二 03 讲「统一响应体与错误码」设计。
 * <p>
 * 分层约定：HTTP 状态码管「通信是否正常」，本类的业务 code 管「业务是否成功」。
 * code = 0 表示业务成功；非 0 表示业务失败（错误码分段在 03 讲展开）。失败响应只接受
 * {@link ErrorCode}，使业务码的数值定义集中在一个位置，调用方不能散落硬编码数字。
 *
 * @param <T> 业务数据类型
 */
public class Result<T> {

    /** 成功响应的固定协议码；失败码由 {@link ErrorCode} 集中定义。 */
    public static final int SUCCESS_CODE = 0;
    public static final String SUCCESS_MESSAGE = "success";

    /** 业务状态码：0 = 成功，非 0 = 业务失败 */
    private final int code;

    private final String message;

    private final T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> ok() {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    /**
     * 使用错误分类的默认客户端文案构造失败响应。
     *
     * @param errorCode 稳定的业务错误分类
     * @param <T> 业务数据类型
     * @return 不携带业务数据的失败响应
     */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        return fail(errorCode, errorCode.getMessage());
    }

    /**
     * 使用稳定错误码和受控业务文案构造失败响应。
     * <p>
     * 允许业务层补充“用户不存在”等具体语义，但数字仍只能由 {@link ErrorCode} 提供，
     * 以免同一种失败在不同接口中出现不同业务码。
     *
     * @param errorCode 稳定的业务错误分类
     * @param message 面向客户端的受控提示，不能传入底层异常信息
     * @param <T> 业务数据类型
     * @return 不携带业务数据的失败响应
     */
    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        ErrorCode resolvedErrorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        String resolvedMessage = Objects.requireNonNull(message, "message must not be null");
        return new Result<>(resolvedErrorCode.getCode(), resolvedMessage, null);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }
}
