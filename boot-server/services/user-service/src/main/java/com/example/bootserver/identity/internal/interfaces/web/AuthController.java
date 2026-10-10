package com.example.bootserver.identity.internal.interfaces.web;

import com.example.bootserver.common.result.Result;
import com.example.bootserver.identity.internal.interfaces.web.dto.LoginRequest;
import com.example.bootserver.identity.internal.interfaces.web.dto.LoginResponse;
import com.example.bootserver.identity.internal.interfaces.web.dto.RegisterRequest;
import com.example.bootserver.identity.internal.application.command.AccessToken;
import com.example.bootserver.identity.internal.application.command.AccessTokenIssuer;
import com.example.bootserver.identity.internal.application.command.LoginCommand;
import com.example.bootserver.identity.internal.application.command.RegisterCommand;
import com.example.bootserver.identity.internal.application.command.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口 —— 注册与后续登录统一放在 /auth 之下，与用户资源 CRUD（/users）分开。
 * <p>
 * 外部前缀由 {@code spring.mvc.servlet.path} 提供，这里只声明资源路径；真实对外地址由运行时配置
 * 与本类路径组合。@Valid 先做字段校验，
 * 业务规则交给 Service，JWT 的签发交给独立的令牌服务。
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "认证", description = "无需 Bearer JWT 的注册与登录入口")
public class AuthController {

    private final UserService userService;
    private final AccessTokenIssuer accessTokenIssuer;

    public AuthController(UserService userService, AccessTokenIssuer accessTokenIssuer) {
        this.userService = userService;
        this.accessTokenIssuer = accessTokenIssuer;
    }

    /**
     * 注册：POST /auth/register（外部前缀由运行时配置决定）。
     * <p>
     * 请求体只接受 {@link RegisterRequest} 白名单字段；角色绑定由服务端按“默认 USER”
     * 规则完成，客户端无法通过请求体指定角色。成功返回新用户的自增主键 ID。
     */
    @PostMapping("/register")
    @Operation(summary = "注册用户", description = "创建账号并自动绑定 USER 角色，返回新用户 ID。")
    public Result<Long> registerUser(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(userService.register(
                new RegisterCommand(request.username(), request.email(), request.password())));
    }

    /**
     * 登录：POST /auth/login（外部前缀由运行时配置决定）。
     *
     * Controller 只串联“认证”与“签发”两个服务：认证失败的统一语义由 UserService 抛出，
     * 这里不根据用户是否存在分支，从入口层避免把账号枚举信息泄露给客户端。
     */
    @PostMapping("/login")
    @Operation(summary = "登录并签发 JWT", description = "校验用户名和密码，成功后返回短期访问令牌与过期时间。")
    public Result<LoginResponse> loginUser(@Valid @RequestBody LoginRequest request) {
        Long userId = userService.authenticate(new LoginCommand(request.username(), request.password()));
        AccessToken token = accessTokenIssuer.issueAccessToken(userId);
        return Result.ok(new LoginResponse(token.value(), token.expiresAt()));
    }
}
