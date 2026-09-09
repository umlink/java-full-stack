package com.example.bootserver.security;

import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.common.result.Result;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Security 层的未认证响应出口。
 *
 * ControllerAdvice 无法处理过滤器链提前拦截的请求，因此由 AuthenticationEntryPoint 直接写入与业务层一致的 Result。
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authenticationException) throws IOException, ServletException {
        // 认证失败不返回异常详情，避免向调用方暴露令牌的解析或验签信息。
        response.setStatus(ErrorCode.UNAUTHORIZED.getHttpStatus());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Result.fail(ErrorCode.UNAUTHORIZED));
    }
}
