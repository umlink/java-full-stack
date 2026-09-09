package com.example.bootserver.common.error;

import java.util.Objects;

/**
 * 预期业务失败的跨层表达，最终由 Web 层转换为统一响应。
 * <p>
 * Service 可以在发现规则不满足时抛出本异常，而无需知道 HTTP 或 JSON 的存在；
 * {@code GlobalExceptionHandler} 再依据 {@link ErrorCode} 选择 HTTP 状态和业务码。
 * 这样业务规则不会被 Controller 的响应拼装逻辑稀释。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        // 业务异常的 message 是面向客户端的受控文案，不能直接透传底层异常信息。
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
