package com.example.bootserver.identity.internal.application.query;

import java.time.LocalDateTime;

/** 用户查询投影；密码哈希永远不进入查询模型。 */
public record UserView(
        Long id,
        String username,
        String name,
        String email,
        Integer status,
        Integer age,
        Integer deleted,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
