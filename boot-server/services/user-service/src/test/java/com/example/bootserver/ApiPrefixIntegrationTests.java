package com.example.bootserver;

import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.common.result.Result;
import com.example.bootserver.config.ServletPathProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本集成测试类专用的非默认 MVC 前缀。类级注解参数先于类声明求值，
 * 而 Java 文件顶层只能声明类型，因此用常量接口承载该测试常量。
 */
interface ApiPrefixIntegrationTestPaths {
    String TEST_SERVLET_PATH = "/gateway";
}

/**
 * 验证配置化 MVC 前缀作用于真实 HTTP 服务，而非只在 Controller 单测中看起来正确。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:api-prefix-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.sql.init.mode=always",
                ServletPathProperties.PATH_PROPERTY + "=" + ApiPrefixIntegrationTestPaths.TEST_SERVLET_PATH
        }
)
class ApiPrefixIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private ServletPathProperties servletPathProperties;

    @Test
    void usersEndpointIsOnlyAvailableThroughConfiguredApiPrefix() throws Exception {
        // 此类以非默认前缀启动；真实 URL 必须由绑定后的运行时配置与资源路径组合。
        HttpResponse<String> prefixedResponse = get(externalPath("/users"));
        HttpResponse<String> unprefixedResponse = get("/users");

        // 用户资源是受保护资源；前缀正确不代表可绕过认证。
        assertThat(prefixedResponse.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(prefixedResponse.body()).contains("\"code\":" + ErrorCode.UNAUTHORIZED.getCode());
        assertThat(unprefixedResponse.statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void registerEndpointIsOnlyAvailableThroughConfiguredApiPrefix() throws Exception {
        String body = "{\"username\":\"prefix-user\",\"email\":\"prefix-user@example.com\",\"password\":\"secret123\"}";

        HttpResponse<String> prefixedResponse = postJson(externalPath("/auth/register"), body);
        HttpResponse<String> unprefixedResponse = postJson("/auth/register", body);

        assertThat(prefixedResponse.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(prefixedResponse.body()).contains("\"code\":" + Result.SUCCESS_CODE);
        assertThat(unprefixedResponse.statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void loginEndpointIssuesTokenOnlyThroughConfiguredApiPrefix() throws Exception {
        String registration = "{\"username\":\"login-prefix-user\",\"email\":\"login-prefix-user@example.com\",\"password\":\"secret123\"}";
        HttpResponse<String> registerResponse = postJson(externalPath("/auth/register"), registration);
        assertThat(registerResponse.statusCode()).isEqualTo(HttpStatus.OK.value());

        String login = "{\"username\":\"login-prefix-user\",\"password\":\"secret123\"}";
        HttpResponse<String> prefixedResponse = postJson(externalPath("/auth/login"), login);
        HttpResponse<String> unprefixedResponse = postJson("/auth/login", login);

        assertThat(prefixedResponse.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(prefixedResponse.body()).contains("\"code\":" + Result.SUCCESS_CODE, "\"accessToken\"");
        assertThat(unprefixedResponse.statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void openApiJsonAndSwaggerUiAreAvailableThroughConfiguredApiPrefix() throws Exception {
        HttpResponse<String> apiDocs = get(externalPath("/v3/api-docs"));
        HttpResponse<String> swaggerUi = get(externalPath("/swagger-ui/index.html"));

        // SpringDoc 的资源也由 DispatcherServlet 提供，因此必须和业务接口使用同一配置前缀。
        assertThat(apiDocs.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(apiDocs.body()).contains("\"/auth/register\"", "\"/users\"", "\"bearerAuth\"");
        assertThat(swaggerUi.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(swaggerUi.body()).contains("Swagger UI");
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void externalPathNormalizesConfiguredAndResourceBoundarySlashes() {
        ServletPathProperties properties = new ServletPathProperties();

        properties.setPath("gateway/");
        assertThat(properties.toExternalPath("/users")).isEqualTo("/gateway/users");

        properties.setPath("/gateway/");
        assertThat(properties.toExternalPath("//users")).isEqualTo("/gateway/users");

        properties.setPath("/");
        assertThat(properties.toExternalPath("users")).isEqualTo("/users");

        properties.setPath(null);
        assertThat(properties.toExternalPath("/users")).isEqualTo("/users");
    }

    private String externalPath(String resourcePath) {
        return servletPathProperties.toExternalPath(resourcePath);
    }
}
