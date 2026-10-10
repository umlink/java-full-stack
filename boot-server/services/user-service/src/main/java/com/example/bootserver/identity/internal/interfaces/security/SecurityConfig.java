package com.example.bootserver.identity.internal.interfaces.security;

import com.example.bootserver.identity.internal.domain.PermissionCodes;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * API 的最小安全过滤链。
 *
 * 认证（确认请求是谁）由 JwtAuthenticationFilter 完成；授权（确认能做什么）按资源路径使用数据库权限码判断。
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                // JWT 随每次请求携带，服务端不保存会话；重启或多实例部署时也不依赖 Session。
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 浏览器表单 Cookie 场景才需要 CSRF；本项目使用 Authorization Header，因此关闭以避免阻断写接口。
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        // Security 匹配 DispatcherServlet 内部资源路径；MVC 外部前缀由配置挂载，不能在此拼接。
                        .requestMatchers("/auth/**", "/h2-console/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error")
                        .permitAll()
                        // 个人身份自查只需合法登录；放在 /users/** 前，避免被管理规则覆盖。
                        .requestMatchers("/users/me").authenticated()
                        // 写接口先匹配管理员权限，再开放已登录用户的商品浏览。
                        .requestMatchers(HttpMethod.POST, "/products").hasAuthority(PermissionCodes.PRODUCT_MANAGEMENT)
                        .requestMatchers(HttpMethod.PUT, "/products/*/status", "/skus/*/price")
                        .hasAuthority(PermissionCodes.PRODUCT_MANAGEMENT)
                        // 库存设置与管理查询均不向普通登录用户开放。
                        .requestMatchers("/stocks/**").hasAuthority(PermissionCodes.PRODUCT_MANAGEMENT)
                        // 后台订单查询与发货只向订单管理员开放，必须先于本人订单规则匹配。
                        .requestMatchers("/admin/orders", "/admin/orders/**")
                        .hasAuthority(PermissionCodes.ORDER_MANAGEMENT)
                        .requestMatchers(HttpMethod.GET, "/products", "/products/**").authenticated()
                        // 购物车始终使用 JWT 主体身份；不允许客户端指定或访问其他用户的数据。
                        .requestMatchers("/cart", "/cart/**").authenticated()
                        .requestMatchers("/orders", "/orders/**").authenticated()
                        // 用户资源属于后台管理面：创建、查询、更新、删除都必须具备稳定权限码。
                        .requestMatchers("/users/**").hasAuthority(PermissionCodes.USER_MANAGEMENT)
                        .anyRequest().authenticated())
                // 放在用户名密码认证过滤器之前，使 Authorization Header 在授权判断前写入 SecurityContext。
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // 开发用 H2 Console 运行在 iframe 中；同源允许帧嵌入，仍保留其他安全响应头。
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
                .build();
    }
}
