package com.example.bootserver.security;

/** HTTP Bearer 认证方案的固定协议片段，供过滤器与真实 HTTP 测试共用。 */
public final class BearerAuthentication {

    public static final String SCHEME_PREFIX = "Bearer ";

    private BearerAuthentication() {
    }
}
