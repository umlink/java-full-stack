package com.example.bootserver.handler;

import com.example.bootserver.common.error.BusinessException;
import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.common.result.Result;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将 Web 层异常收口为固定的 {@link Result} 契约。
 * <p>
 * 它位于服务模块而非 common-core：异常到 HTTP 的映射属于传输层职责，公共模块保持
 * 纯 Java 才能被未来的消息消费、任务调度等非 HTTP 入口复用。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException exception) {
        // 这是预期失败：业务层已给出稳定分类和受控提示，可安全返回给客户端。
        ErrorCode errorCode = exception.getErrorCode();
        return failure(errorCode, exception.getMessage());
    }

    /**
     * 数据库唯一键是并发场景下防重复的最后防线。
     *
     * 业务预检查同时通过时，后一个事务仍可能在 INSERT 时触发此异常；统一转为冲突，
     * 避免客户端把可预期的重复注册误判为系统故障。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> handleDuplicateKeyException(DuplicateKeyException exception) {
        return failure(ErrorCode.CONFLICT, ErrorCode.CONFLICT.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidationException(MethodArgumentNotValidException exception) {
        // 一次只返回第一个字段错误，客户端修正后再提交，避免响应结构随着字段数变化。
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null ? ErrorCode.PARAMETER_ERROR.getMessage() : fieldError.getDefaultMessage();
        return failure(ErrorCode.PARAMETER_ERROR, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleUnreadableMessage(HttpMessageNotReadableException exception) {
        // Jackson 的解析细节可能含字段和类型信息，不应直接暴露给调用方。
        return failure(ErrorCode.PARAMETER_ERROR, "请求体格式错误");
    }

    /** 处理 @RequestParam、@PathVariable 上的约束；它们不经过 @RequestBody 的绑定异常。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolationException(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse(ErrorCode.PARAMETER_ERROR.getMessage());
        return failure(ErrorCode.PARAMETER_ERROR, message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpectedException(Exception exception) {
        // 日志保留排障现场；响应只保留稳定文案，避免泄露 SQL、堆栈或配置。
        LOGGER.error("Unhandled exception", exception);
        return failure(ErrorCode.SYSTEM_ERROR, ErrorCode.SYSTEM_ERROR.getMessage());
    }

    private ResponseEntity<Result<Void>> failure(ErrorCode errorCode, String message) {
        // HTTP 状态用于协议和网关处理，业务码用于前端决定提示与交互，两者不可互相替代。
        return ResponseEntity.status(HttpStatus.valueOf(errorCode.getHttpStatus()))
                .body(Result.fail(errorCode, message));
    }
}
