package com.example.bootserver.service;

import com.example.bootserver.common.error.BusinessException;
import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.controller.dto.LoginRequest;
import com.example.bootserver.controller.dto.LoginResponse;
import com.example.bootserver.controller.dto.RegisterRequest;
import com.example.bootserver.entity.User;
import com.example.bootserver.security.JwtProperties;
import com.example.bootserver.security.JwtTokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 登录集成测试：用真实 BCrypt、H2 和 JJWT 验证凭据校验与令牌内容，而不是只模拟服务返回值。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:login-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "app.jwt.access-token-ttl=PT5M"
})
class LoginFlowIntegrationTests {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void correctCredentialsIssueSignedTokenWithUserIdAndConfiguredExpiration() {
        Long userId = userService.register(new RegisterRequest("login-user", "login-user@example.com", "secret123"));
        Instant beforeLogin = Instant.now();

        User authenticatedUser = userService.authenticate(new LoginRequest("login-user", "secret123"));
        LoginResponse response = jwtTokenService.issueAccessToken(authenticatedUser.getId());
        Claims claims = parseClaims(response.accessToken());

        assertThat(claims.getSubject()).isEqualTo(String.valueOf(userId));
        assertThat(claims.getExpiration().toInstant().getEpochSecond())
                .isEqualTo(response.expiresAt().getEpochSecond());
        assertThat(Duration.between(claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant()))
                .isEqualTo(Duration.ofMinutes(5));
        assertThat(response.expiresAt()).isAfter(beforeLogin.plus(Duration.ofMinutes(4)));
    }

    @Test
    void unknownUsernameAndWrongPasswordHaveSameUnauthorizedSemantics() {
        userService.register(new RegisterRequest("known-user", "known-user@example.com", "secret123"));

        BusinessException missingUser = authenticateAndCapture("missing-user", "secret123");
        BusinessException wrongPassword = authenticateAndCapture("known-user", "wrong-password");

        assertThat(missingUser.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(wrongPassword.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(missingUser.getMessage()).isEqualTo("用户名或密码错误");
        assertThat(wrongPassword.getMessage()).isEqualTo(missingUser.getMessage());
    }

    private BusinessException authenticateAndCapture(String username, String password) {
        try {
            userService.authenticate(new LoginRequest(username, password));
        } catch (BusinessException exception) {
            return exception;
        }
        throw new AssertionError("错误凭据必须抛出 BusinessException");
    }

    private Claims parseClaims(String token) {
        SecretKey signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getSecretBase64()));
        // 解析时必须带同一签名密钥：验签失败的 token 在 M1-07 会被过滤器拒绝。
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
