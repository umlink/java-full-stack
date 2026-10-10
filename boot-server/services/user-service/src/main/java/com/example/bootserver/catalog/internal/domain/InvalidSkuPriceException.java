package com.example.bootserver.catalog.internal.domain;

public class InvalidSkuPriceException extends IllegalArgumentException {

    public InvalidSkuPriceException() {
        super("价格不能为负数");
    }
}
