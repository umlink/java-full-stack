package com.example.bootserver.cart.internal.domain;

/** 购物车权威存储不可用或数据损坏；由接口层统一转换为系统错误。 */
public class CartStoreUnavailableException extends RuntimeException {

    public CartStoreUnavailableException(String message) {
        super(message);
    }

    public CartStoreUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
