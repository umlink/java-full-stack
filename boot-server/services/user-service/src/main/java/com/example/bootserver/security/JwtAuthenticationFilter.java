package com.example.bootserver.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 把 Authorization 中的 Bearer JWT 转换为当前请求的 Spring Security 身份。
 *
 * 过滤器先认证“令牌确属本服务签发且未过期”，再从当前 RBAC 关系加载权限，最后交由路由规则授权。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final UserAuthorityService userAuthorityService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService,
                                   UserAuthorityService userAuthorityService,
                                   RestAuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtTokenService = jwtTokenService;
        this.userAuthorityService = userAuthorityService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization)) {
            // 不带凭据时不在这里直接拒绝：公开接口继续执行，受保护接口由后续授权规则统一返回 401。
            filterChain.doFilter(request, response);
            return;
        }
        if (!authorization.startsWith(BearerAuthentication.SCHEME_PREFIX)) {
            reject(request, response, "Authorization 必须使用 Bearer 方案", null);
            return;
        }

        String token = authorization.substring(BearerAuthentication.SCHEME_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            reject(request, response, "Bearer token 不能为空", null);
            return;
        }

        try {
            Long userId = jwtTokenService.parseUserId(token);
            // 令牌有效不等于账号仍有效：停用或逻辑删除后，下一次请求即按未认证处理。
            if (!userAuthorityService.isActiveUser(userId)) {
                SecurityContextHolder.clearContext();
                reject(request, response, "JWT 主体已失效", null);
                return;
            }
            // 权限每次从关系表读取：管理员撤销角色后，下一个请求不会继续沿用 JWT 签发时的旧权限。
            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(userId, null,
                            userAuthorityService.loadAuthorities(userId));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            // SecurityContext 只在当前请求线程内可见，STATELESS 配置确保它不会写入 HttpSession。
            SecurityContextHolder.setContext(context);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            reject(request, response, "JWT 无效或已过期", exception);
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String reason,
                        Exception exception) throws IOException, ServletException {
        // reason 只用于安全框架的内部异常对象；响应统一采用 ErrorCode.UNAUTHORIZED，不能泄露验签细节。
        authenticationEntryPoint.commence(request, response,
                new InsufficientAuthenticationException(reason, exception));
    }
}
