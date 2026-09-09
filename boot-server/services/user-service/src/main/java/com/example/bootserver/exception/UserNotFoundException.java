package com.example.bootserver.exception;

import com.example.bootserver.common.error.BusinessException;
import com.example.bootserver.common.error.ErrorCode;

/** 用户资源不存在时的领域语义，统一交由全局异常处理器返回 40400。 */
public class UserNotFoundException extends BusinessException {

    public UserNotFoundException() {
        super(ErrorCode.NOT_FOUND, "用户不存在");
    }
}
