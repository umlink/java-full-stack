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

        assertThat(prefixedResponse.statusCode()).isEqualTo(200);
        assertThat(prefixedResponse.body()).contains("\"code\":0");
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
