package com.example.bootserver.catalog.internal.domain;

public class InvalidProductStatusException extends IllegalArgumentException {

    public InvalidProductStatusException() {
        super("状态只能为上架或下架");
    }
}
