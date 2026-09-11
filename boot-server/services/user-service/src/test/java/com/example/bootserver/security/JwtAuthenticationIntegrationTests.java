package com.example.bootserver.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.common.result.Result;
import com.example.bootserver.config.ServletPathProperties;
import com.example.bootserver.entity.Role;
import com.example.bootserver.entity.User;
import com.example.bootserver.entity.UserRole;
import com.example.bootserver.mapper.RoleMapper;
import com.example.bootserver.mapper.UserMapper;
import com.example.bootserver.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JWT 认证集成测试：必须经由真实 HTTP Filter Chain，才能验证 SecurityContext 在授权前被正确写入。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:jwt-authentication-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.sql.init.mode=always"
        }
)
class JwtAuthenticationIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ServletPathProperties servletPathProperties;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private UserMapper userMapper;

    @Test
    void protectedEndpointRejectsMissingAndTamperedTokensWithUnifiedUnauthorizedResponse() throws Exception {
        HttpResponse<String> missingToken = getCurrentUser(null);
        assertUnauthorized(missingToken);

        LoginResult validLogin = registerAndLogin("tampered-token-user", "tampered-token-user@example.com");
        // 在紧凑 JWT 后追加字符会破坏签名；过滤器验签失败后不能继续访问受保护资源。
        HttpResponse<String> tamperedToken = getCurrentUser(validLogin.accessToken() + "x");
        assertUnauthorized(tamperedToken);

        // 即使令牌使用正确密钥签名，sub 不是有效用户主键时也不能形成认证身份。
        HttpResponse<String> invalidSubject = getCurrentUser(createToken("0", Instant.now().plusSeconds(60)));
        assertUnauthorized(invalidSubject);
    }

    @Test
    void protectedEndpointRejectsExpiredTokenAndAllowsValidToken() throws Exception {
        HttpResponse<String> expiredToken = getCurrentUser(createExpiredToken());
        assertUnauthorized(expiredToken);

        LoginResult validLogin = registerAndLogin("valid-token-user", "valid-token-user@example.com");
        HttpResponse<String> authenticatedResponse = getCurrentUser(validLogin.accessToken());

        assertThat(authenticatedResponse.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(authenticatedResponse.body()).contains("\"code\":" + Result.SUCCESS_CODE, "\"data\":" + validLogin.userId());
    }

    @Test
    void managementEndpointDistinguishesMissingUserAndAdminAuthorities() throws Exception {
        // 同一路由验证三种安全语义：没有身份、身份不足、具备后台权限。
        assertUnauthorized(get(externalPath("/users"), null));

        LoginResult ordinaryUser = registerAndLogin("ordinary-authority-user", "ordinary-authority-user@example.com");
        HttpResponse<String> forbidden = get(externalPath("/users"), ordinaryUser.accessToken());
        assertThat(forbidden.statusCode()).isEqualTo(ErrorCode.FORBIDDEN.getHttpStatus());
        assertThat(forbidden.body()).contains("\"code\":" + ErrorCode.FORBIDDEN.getCode(), "\"message\":\"没有访问权限\"");

        LoginResult administrator = registerAndLogin("admin-authority-user", "admin-authority-user@example.com");
        grantAdministratorRole(administrator.userId());
        HttpResponse<String> allowed = get(externalPath("/users"), administrator.accessToken());

        assertThat(allowed.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(allowed.body()).contains("\"code\":" + Result.SUCCESS_CODE);
    }

    @Test
    void oldTokenIsRejectedAfterItsUserIsDisabled() throws Exception {
        LoginResult user = registerAndLogin("disabled-token-user", "disabled-token-user@example.com");
        User disabled = new User();
        disabled.setId(user.userId());
        disabled.setStatus(User.STATUS_DISABLED);
        assertThat(userMapper.updateById(disabled)).isEqualTo(1);

        // 已签发的 JWT 不再足以证明有效身份，个人接口与管理接口都必须在过滤器处返回 401。
        assertUnauthorized(getCurrentUser(user.accessToken()));
        assertUnauthorized(get(externalPath("/users"), user.accessToken()));
    }

    @Test
    void oldTokenIsRejectedAfterItsUserIsLogicallyDeleted() throws Exception {
        LoginResult user = registerAndLogin("deleted-token-user", "deleted-token-user@example.com");
        assertThat(userMapper.deleteById(user.userId())).isEqualTo(1);

        assertUnauthorized(getCurrentUser(user.accessToken()));
        assertUnauthorized(get(externalPath("/users"), user.accessToken()));
    }

    private LoginResult registerAndLogin(String username, String email) throws Exception {
        HttpResponse<String> registration = postJson(externalPath("/auth/register"), """
                {"username":"%s","email":"%s","password":"secret123"}
                """.formatted(username, email));
        assertThat(registration.statusCode()).isEqualTo(HttpStatus.OK.value());
        long userId = objectMapper.readTree(registration.body()).path("data").asLong();

        HttpResponse<String> login = postJson(externalPath("/auth/login"), """
                {"username":"%s","password":"secret123"}
                """.formatted(username));
        assertThat(login.statusCode()).isEqualTo(HttpStatus.OK.value());
        // 使用应用同一套 Jackson 解析响应，避免用字符串截取把 JSON 结构当作普通文本处理。
        String accessToken = objectMapper.readTree(login.body()).path("data").path("accessToken").asString();
        return new LoginResult(userId, accessToken);
    }

    private String createExpiredToken() {
        return createToken("1", Instant.now().minusSeconds(60));
    }

    private String createToken(String userId, Instant expiration) {
        SecretKey signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getSecretBase64()));
        return Jwts.builder()
                .subject(userId)
                .issuedAt(Date.from(expiration.minusSeconds(60)))
                .expiration(Date.from(expiration))
                .signWith(signingKey)
                .compact();
    }

    private HttpResponse<String> getCurrentUser(String accessToken) throws Exception {
        return get(externalPath("/users/me"), accessToken);
    }

    private HttpResponse<String> get(String path, String accessToken) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET();
        if (accessToken != null) {
            request.header(HttpHeaders.AUTHORIZATION, BearerAuthentication.SCHEME_PREFIX + accessToken);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void grantAdministratorRole(long userId) {
        Role administratorRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, RoleCodes.ADMIN));
        UserRole binding = new UserRole();
        binding.setUserId(userId);
        binding.setRoleId(administratorRole.getId());
        userRoleMapper.insert(binding);
    }

    private HttpResponse<String> postJson(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void assertUnauthorized(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.body()).contains("\"code\":" + ErrorCode.UNAUTHORIZED.getCode(), "\"message\":\"请先登录\"");
    }

    private String externalPath(String resourcePath) {
        return servletPathProperties.toExternalPath(resourcePath);
    }

    private record LoginResult(long userId, String accessToken) {
    }
}
