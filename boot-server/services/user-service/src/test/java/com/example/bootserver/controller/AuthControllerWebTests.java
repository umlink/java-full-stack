package com.example.bootserver.controller;

import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.common.error.BusinessException;
import com.example.bootserver.common.result.Result;
import com.example.bootserver.controller.dto.LoginRequest;
import com.example.bootserver.controller.dto.LoginResponse;
import com.example.bootserver.controller.dto.RegisterRequest;
import com.example.bootserver.entity.User;
import com.example.bootserver.security.RoleCodes;
import com.example.bootserver.handler.GlobalExceptionHandler;
import com.example.bootserver.security.JwtTokenService;
import com.example.bootserver.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注册接口的 Web 层测试：聚焦 JSON 绑定、字段白名单和参数校验，
 * 不依赖数据库；业务规则（重复检查、BCrypt、绑定角色）由 Service 集成测试覆盖。
 */
class AuthControllerWebTests {

    private final UserService userService = mock(UserService.class);
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(userService, jwtTokenService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void registerBindsDtoAndReturnsNewUserId() throws Exception {
        when(userService.register(any(RegisterRequest.class))).thenAnswer(invocation -> {
            RegisterRequest request = invocation.getArgument(0);
            // 验证 Controller 只把 DTO 白名单字段传给 Service，不带客户端可篡改的额外信息
            assertThat(request.username()).isEqualTo("dave");
            assertThat(request.email()).isEqualTo("dave@example.com");
            assertThat(request.password()).isEqualTo("secret123");
            return 200L;
        });

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"dave","email":"dave@example.com","password":"secret123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS_CODE))
                .andExpect(jsonPath("$.data").value(200));

        verify(userService).register(any(RegisterRequest.class));
    }

    @Test
    void registerIgnoresClientSuppliedRoleAndAdminFields() throws Exception {
        // 防止客户端通过 role/roles 越权指定 ADMIN：DTO 白名单不提供这些字段，
        // 即使 JSON 中出现也会被 Jackson ignoreUnknown 丢弃，Service 永远看不到。
        when(userService.register(any(RegisterRequest.class))).thenAnswer(invocation -> {
            RegisterRequest request = invocation.getArgument(0);
            assertThat(request.username()).isEqualTo("dave");
            return 201L;
        });

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"dave","email":"dave@example.com","password":"secret123","role":"%s","roles":[{"id":1},{"id":2}]}
                                """.formatted(RoleCodes.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(201));
    }

    @Test
    void registerReturnsConflictContractWhenUsernameAlreadyExists() throws Exception {
        when(userService.register(any(RegisterRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.CONFLICT, "用户名已注册"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"dave","email":"dave@example.com","password":"secret123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT.getCode()))
                .andExpect(jsonPath("$.message").value("用户名已注册"));
    }

    @Test
    void loginReturnsAccessTokenForCorrectCredentials() throws Exception {
        User user = new User();
        user.setId(300L);
        LoginResponse loginResponse = new LoginResponse("signed.jwt.token", java.time.Instant.parse("2026-09-09T12:30:00Z"));
        when(userService.authenticate(any(LoginRequest.class))).thenAnswer(invocation -> {
            LoginRequest request = invocation.getArgument(0);
            assertThat(request.username()).isEqualTo("dave");
            assertThat(request.password()).isEqualTo("secret123");
            return user;
        });
        when(jwtTokenService.issueAccessToken(300L)).thenReturn(loginResponse);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"dave","password":"secret123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS_CODE))
                .andExpect(jsonPath("$.data.accessToken").value("signed.jwt.token"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-09-09T12:30:00Z"));

        verify(userService).authenticate(any(LoginRequest.class));
        verify(jwtTokenService).issueAccessToken(300L);
    }

    @Test
    void loginUsesSameUnauthorizedContractForInvalidCredentials() throws Exception {
        when(userService.authenticate(any(LoginRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"missing-user","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));

        // 未认证就不签发令牌，避免失败路径意外产生可被误用的凭据。
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void registerRejectsBlankUsernameBeforeCallingService() throws Exception {
        assertInvalidRegisterRequest(
                """
                        {"username":"   ","email":"dave@example.com","password":"secret123"}
                        """,
                "用户名不能为空");
    }

    @Test
    void registerRejectsBlankEmailBeforeCallingService() throws Exception {
        assertInvalidRegisterRequest(
                """
                        {"username":"dave","email":"","password":"secret123"}
                        """,
                "邮箱不能为空");
    }

    @Test
    void registerRejectsMalformedEmailBeforeCallingService() throws Exception {
        assertInvalidRegisterRequest(
                """
                        {"username":"dave","email":"not-an-email","password":"secret123"}
                        """,
                "邮箱格式不正确");
    }

    @Test
    void registerRejectsBlankPasswordBeforeCallingService() throws Exception {
        assertInvalidRegisterRequest(
                """
                        {"username":"dave","email":"dave@example.com","password":" "}
                        """,
                "密码不能为空");
    }

    @Test
    void registerRejectsUsernameLongerThanDatabaseColumnBeforeCallingService() throws Exception {
        assertInvalidRegisterRequest(
                """
                        {"username":"%s","email":"dave@example.com","password":"secret123"}
                        """.formatted("u".repeat(65)),
                "用户名不能超过 64 个字符");
    }

    @Test
    void registerRejectsEmailLongerThanDatabaseColumnBeforeCallingService() throws Exception {
        assertInvalidRegisterRequest(
                """
                        {"username":"dave","email":"%s","password":"secret123"}
                        """.formatted("a".repeat(117) + "@example.com"),
                "邮箱不能超过 128 个字符");
    }

    private void assertInvalidRegisterRequest(String requestBody, String expectedMessage) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAMETER_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value(expectedMessage));

        // 校验失败在进入 Controller 方法前中断，Service 绝不能被调用
        verifyNoInteractions(userService);
    }
}
