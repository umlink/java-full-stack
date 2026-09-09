package com.example.bootserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证配置化 MVC 前缀作用于真实 HTTP 服务，而非只在 Controller 单测中看起来正确。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:api-prefix-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.sql.init.mode=always"
        }
)
class ApiPrefixIntegrationTests {

    @LocalServerPort
    private int port;

    @Test
    void usersEndpointIsOnlyAvailableThroughConfiguredApiPrefix() throws Exception {
        // spring.mvc.servlet.path 把 DispatcherServlet 挂在 /api；Controller 的 /users 变为外部 /api/users。
        HttpResponse<String> prefixedResponse = get("/api/users");
        HttpResponse<String> unprefixedResponse = get("/users");

        // M1-07 后，/api/users 是受保护资源；前缀正确不代表可绕过认证。
        assertThat(prefixedResponse.statusCode()).isEqualTo(401);
        assertThat(prefixedResponse.body()).contains("\"code\":40100");
        assertThat(unprefixedResponse.statusCode()).isEqualTo(404);
    }

    @Test
    void registerEndpointIsOnlyAvailableThroughConfiguredApiPrefix() throws Exception {
        String body = "{\"username\":\"prefix-user\",\"email\":\"prefix-user@example.com\",\"password\":\"secret123\"}";

        HttpResponse<String> prefixedResponse = postJson("/api/auth/register", body);
        HttpResponse<String> unprefixedResponse = postJson("/auth/register", body);

        assertThat(prefixedResponse.statusCode()).isEqualTo(200);
        assertThat(prefixedResponse.body()).contains("\"code\":0");
        assertThat(unprefixedResponse.statusCode()).isEqualTo(404);
    }

    @Test
    void loginEndpointIssuesTokenOnlyThroughConfiguredApiPrefix() throws Exception {
        String registration = "{\"username\":\"login-prefix-user\",\"email\":\"login-prefix-user@example.com\",\"password\":\"secret123\"}";
        HttpResponse<String> registerResponse = postJson("/api/auth/register", registration);
        assertThat(registerResponse.statusCode()).isEqualTo(200);

        String login = "{\"username\":\"login-prefix-user\",\"password\":\"secret123\"}";
        HttpResponse<String> prefixedResponse = postJson("/api/auth/login", login);
        HttpResponse<String> unprefixedResponse = postJson("/auth/login", login);

        assertThat(prefixedResponse.statusCode()).isEqualTo(200);
        assertThat(prefixedResponse.body()).contains("\"code\":0", "\"accessToken\"");
        assertThat(unprefixedResponse.statusCode()).isEqualTo(404);
    }

    @Test
    void openApiJsonAndSwaggerUiAreAvailableThroughConfiguredApiPrefix() throws Exception {
        HttpResponse<String> apiDocs = get("/api/v3/api-docs");
        HttpResponse<String> swaggerUi = get("/api/swagger-ui/index.html");

        // SpringDoc 的资源也由 DispatcherServlet 提供，因此必须和业务接口一样经过 /api 前缀。
        assertThat(apiDocs.statusCode()).isEqualTo(200);
        assertThat(apiDocs.body()).contains("\"/auth/register\"", "\"/users\"", "\"bearerAuth\"");
        assertThat(swaggerUi.statusCode()).isEqualTo(200);
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
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
