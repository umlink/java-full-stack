package com.example.bootserver.cart.internal.infrastructure.redis;

import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.common.result.Result;
import com.example.bootserver.config.ServletPathProperties;
import com.example.bootserver.identity.internal.interfaces.security.BearerAuthentication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Redis 故障时购物车返回稳定系统错误，且不会静默改用旧数据库表。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:redis-cart-unavailable-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "app.cart.redis.enabled=true",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=1",
        "spring.data.redis.connect-timeout=PT0.1S",
        "spring.data.redis.timeout=PT0.1S",
        "management.health.redis.enabled=false"
})
class RedisCartUnavailableIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private ServletPathProperties servletPathProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void unavailableRedisReturnsSystemErrorWithoutReadingOrWritingLegacyCart() throws Exception {
        String username = "redis-cart-unavailable";
        assertSuccess(post("/auth/register", """
                {"username":"%s","email":"%s@example.com","password":"secret123"}
                """.formatted(username, username), null));
        String token = successData(post("/auth/login", """
                {"username":"%s","password":"secret123"}
                """.formatted(username), null)).path("accessToken").asString();
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_user WHERE username = ?", Long.class, username);
        Long skuId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_sku WHERE sku_code = 'BOOTMALL-BOOK-STD'", Long.class);
        jdbcTemplate.update("INSERT INTO t_cart_item (user_id, sku_id, quantity) VALUES (?, ?, ?)",
                userId, skuId, 7);

        HttpResponse<String> response = get("/cart", token);
        assertThat(response.statusCode()).isEqualTo(ErrorCode.SYSTEM_ERROR.getHttpStatus());
        assertThat(objectMapper.readTree(response.body()).path("code").asInt())
                .isEqualTo(ErrorCode.SYSTEM_ERROR.getCode());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT quantity FROM t_cart_item WHERE user_id = ? AND sku_id = ?",
                Integer.class, userId, skuId)).isEqualTo(7);
    }

    private JsonNode successData(HttpResponse<String> response) {
        assertSuccess(response);
        return objectMapper.readTree(response.body()).path("data");
    }

    private void assertSuccess(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(response.body()).path("code").asInt()).isEqualTo(Result.SUCCESS_CODE);
    }

    private HttpResponse<String> get(String resourcePath, String token) throws Exception {
        return httpClient.send(request(resourcePath, token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String resourcePath, String body, String token) throws Exception {
        return httpClient.send(request(resourcePath, token)
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String resourcePath, String token) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + port + servletPathProperties.toExternalPath(resourcePath)))
                .header(HttpHeaders.CONTENT_TYPE, "application/json");
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, BearerAuthentication.SCHEME_PREFIX + token);
        }
        return request;
    }
}
