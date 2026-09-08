package com.example.bootapp.common.result;

/**
 * 统一响应体 —— 对齐阶段二 03 讲「统一响应体与错误码」设计。
 * <p>
 * 分层约定：HTTP 状态码管「通信是否正常」，本类的业务 code 管「业务是否成功」。
 * code = 0 表示业务成功；非 0 表示业务失败（错误码分段在 03 讲展开）。
 *
 * @param <T> 业务数据类型
 */
public class Result<T> {

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
        return new Result<>(0, "success", null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "success", data);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
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
