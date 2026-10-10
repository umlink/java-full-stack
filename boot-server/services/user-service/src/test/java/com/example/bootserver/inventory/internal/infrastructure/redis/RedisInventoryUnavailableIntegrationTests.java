package com.example.bootserver.inventory.internal.infrastructure.redis;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Redis 不可用时仍由数据库条件更新给出稳定下单结果。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:redis-inventory-unavailable-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "app.cart.redis.enabled=false",
        "app.inventory.redis.enabled=true",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=1",
        "spring.data.redis.connect-timeout=PT0.1S",
        "spring.data.redis.timeout=PT0.1S",
        "management.health.redis.enabled=false"
})
class RedisInventoryUnavailableIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private ServletPathProperties paths;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate db;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void unavailableRedisFallsBackToDatabaseReservation() throws Exception {
        String code = "REDIS-DOWN-" + UUID.randomUUID();
        Long productId = db.queryForObject(
                "SELECT MIN(id) FROM t_product WHERE status = 'ON_SALE'", Long.class);
        db.update("INSERT INTO t_sku (product_id, sku_code, price) VALUES (?, ?, 1.00)", productId, code);
        Long skuId = db.queryForObject("SELECT id FROM t_sku WHERE sku_code = ?", Long.class, code);
        db.update("INSERT INTO t_stock (sku_id, total_count, locked_count) VALUES (?, 1, 0)", skuId);
        String token = login();

        HttpResponse<String> response = send(request("/orders", token)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"idempotencyKey":"%s","items":[{"skuId":%d,"quantity":1}]}
                        """.formatted(UUID.randomUUID(), skuId))).build());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).path("code").asInt()).isEqualTo(Result.SUCCESS_CODE);
        assertThat(db.queryForObject("SELECT locked_count FROM t_stock WHERE sku_id = ?",
                Long.class, skuId)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM t_order", Integer.class)).isPositive();
    }

    private String login() throws Exception {
        String username = "redis-down-" + UUID.randomUUID();
        successData(send(request("/auth/register", null)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"username":"%s","email":"%s@example.com","password":"secret123"}
                        """.formatted(username, username))).build()));
        return successData(send(request("/auth/login", null)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"username":"%s","password":"secret123"}
                        """.formatted(username))).build())).path("accessToken").asString();
    }

    private JsonNode successData(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(response.body());
        assertThat(body.path("code").asInt()).isEqualTo(Result.SUCCESS_CODE);
        return body.path("data");
    }

    private HttpRequest.Builder request(String path, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + port + paths.toExternalPath(path)))
                .header(HttpHeaders.CONTENT_TYPE, "application/json");
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, BearerAuthentication.SCHEME_PREFIX + token);
        }
        return builder;
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
