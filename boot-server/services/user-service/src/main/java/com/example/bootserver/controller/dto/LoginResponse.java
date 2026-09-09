package com.example.bootserver.controller.dto;

import java.time.Instant;

/**
 * 登录成功后返回的短期访问凭据。
 *
 * expiresAt 与 JWT 内的 exp 来自同一时刻，客户端可以在过期前主动回到登录页；不返回密码哈希或角色等敏感数据。
 */
public record LoginResponse(String accessToken, Instant expiresAt) {
}
