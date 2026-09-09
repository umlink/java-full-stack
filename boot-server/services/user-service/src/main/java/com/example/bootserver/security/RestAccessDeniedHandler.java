package com.example.bootserver.security;

import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Security 层的无权限响应出口。
 *
 * 已认证请求在授权阶段被拒绝时，Spring Security 直接调用此处理器；它与未认证入口分开，确保客户端能区分 401 和 403。
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        // 不返回具体缺失的角色或权限，避免暴露后台权限模型。
        response.setStatus(ErrorCode.FORBIDDEN.getHttpStatus());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Result.fail(ErrorCode.FORBIDDEN));
    }
}
