package com.example.bootserver.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * JWT 的运行时配置。密钥由部署环境注入，仓库只保留变量名和令牌时长等非敏感配置。
 * {@code @ConfigurationProperties} 会把 application.yml 中 app.jwt 下的配置绑定到本类字段；
 * 例如 secret-base64 按 Spring 的宽松命名规则映射为 secretBase64。
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    // 绑定 ${JWT_SECRET_BASE64:} 的结果；@NotBlank 在 Spring 创建配置对象时拒绝空值，防止服务带空密钥启动。
    @NotBlank(message = "必须通过 JWT_SECRET_BASE64 配置 JWT 密钥")
    private String secretBase64;

    // 绑定 PT30M 这类 ISO-8601 时长；实际是否为正数由 JwtTokenService 再作业务校验。
    @NotNull
    private Duration accessTokenTtl;

    public String getSecretBase64() {
        return secretBase64;
    }

    public void setSecretBase64(String secretBase64) {
        // Spring Boot 的配置绑定器通过 setter 写入 application.yml 或环境变量中的值。
        this.secretBase64 = secretBase64;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }
}
