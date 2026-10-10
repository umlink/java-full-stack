package com.example.bootserver.identity.internal.domain;

import java.time.LocalDateTime;

/** 身份写模型；密码字段只保存不可逆哈希。 */
public record UserAccount(
        Long id,
        String username,
        String name,
        String email,
        String passwordHash,
        Integer status,
        Integer age,
        Integer deleted,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_DISABLED = 0;
    public static final int NOT_DELETED = 0;

    public static UserAccount registered(String username, String email, String passwordHash) {
        return new UserAccount(null, username, username, email, passwordHash,
                STATUS_ACTIVE, null, NOT_DELETED, null, null);
    }

    public static UserAccount legacy(String name, String email, Integer age) {
        return new UserAccount(null, null, name, email, null,
                null, age, null, null, null);
    }

    public boolean isActive() {
        return Integer.valueOf(STATUS_ACTIVE).equals(status)
                && Integer.valueOf(NOT_DELETED).equals(deleted);
    }
}
