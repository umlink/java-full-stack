package com.example.bootserver.controller;

import com.example.bootserver.common.result.Result;
import com.example.bootserver.controller.dto.LoginRequest;
import com.example.bootserver.controller.dto.LoginResponse;
import com.example.bootserver.controller.dto.RegisterRequest;
import com.example.bootserver.entity.User;
import com.example.bootserver.security.JwtTokenService;
import com.example.bootserver.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口 —— 注册与后续登录统一放在 /auth 之下，与用户资源 CRUD（/users）分开。
 * <p>
 * 外部前缀 /api 仍由 {@code spring.mvc.servlet.path} 提供，这里只声明资源路径，
 * 因此认证接口对外是 POST /api/auth/register 与 POST /api/auth/login。@Valid 先做字段校验，
 * 业务规则交给 Service，JWT 的签发交给独立的令牌服务。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final JwtTokenService jwtTokenService;

    public AuthController(UserService userService, JwtTokenService jwtTokenService) {
        this.userService = userService;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * 注册：POST /api/auth/register。
     * <p>
     * 请求体只接受 {@link RegisterRequest} 白名单字段；角色绑定由服务端按“默认 USER”
     * 规则完成，客户端无法通过请求体指定角色。成功返回新用户的自增主键 ID。
     */
    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(userService.register(request));
    }

    /**
     * 登录：POST /api/auth/login。
     *
     * Controller 只串联“认证”与“签发”两个服务：认证失败的统一语义由 UserService 抛出，
     * 这里不根据用户是否存在分支，从入口层避免把账号枚举信息泄露给客户端。
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userService.authenticate(request);
        return Result.ok(jwtTokenService.issueAccessToken(user.getId()));
    }
}
