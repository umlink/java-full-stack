package com.example.bootserver.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * M1 接口文档的全局元信息与 JWT 安全方案。
 *
 * SpringDoc 在启动时读取本配置、Controller 映射和 DTO 约束，生成由 MVC 外部前缀配置决定的 OpenAPI JSON；Swagger UI
 * 只是该 JSON 的可视化页面，实际鉴权仍由 Spring Security 的过滤器链执行。
 */
@Configuration
@SecurityScheme(
        name = OpenApiConfig.BEARER_AUTHENTICATION,
        type = SecuritySchemeType.HTTP,
        in = SecuritySchemeIn.HEADER,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {

    /** 受保护接口引用的 OpenAPI 安全方案名，避免 Controller 各自硬编码。 */
    public static final String BEARER_AUTHENTICATION = "bearerAuth";

    @Bean
    public OpenAPI bootMallOpenApi() {
        return new OpenAPI().info(new Info()
                .title("BootMall 用户服务 API")
                .version("M1")
                .description("M1 安全后台骨架：注册、登录、JWT 认证与 RBAC 用户管理接口。"));
    }
}
