package com.example.bootserver.catalog.internal.infrastructure.cache;

import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.common.result.Result;
import com.example.bootserver.config.ServletPathProperties;
import com.example.bootserver.identity.internal.domain.RoleCodes;
import com.example.bootserver.identity.internal.interfaces.security.BearerAuthentication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** 仅在显式提供真实 Redis 端口时运行，验证网络存储、TTL、事务后失效和指标端点。 */
@EnabledIfEnvironmentVariable(named = "BOOT_REDIS_TEST_PORT", matches = "\\d+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:product-cache-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "app.catalog-cache.enabled=true",
        "app.catalog-cache.product-detail-ttl=PT2M",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=${BOOT_REDIS_TEST_PORT}"
})
class ProductDetailsRedisIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private ServletPathProperties servletPathProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    @AfterEach
    void clearProductCache() {
        Set<String> keys = redisTemplate.keys(RedisProductDetailsCache.PRODUCT_DETAILS_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void realRedisCachesDetailsExpiresAndInvalidatesAfterWritesWithObservableMetrics() throws Exception {
        String userToken = registerAndLogin("redis-product-reader", false);
        String adminToken = registerAndLogin("redis-product-admin", true);
        Long productId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_product WHERE status = 'ON_SALE' ORDER BY id LIMIT 1", Long.class);
        Long skuId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_sku WHERE product_id = ? ORDER BY id LIMIT 1", Long.class, productId);
        Integer skuVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM t_sku WHERE id = ?", Integer.class, skuId);
        String key = RedisProductDetailsCache.PRODUCT_DETAILS_KEY_PREFIX + productId;

        JsonNode firstRead = successData(get("/products/" + productId, userToken));
        assertThat(firstRead.path("id").asLong()).isEqualTo(productId);
        assertThat(redisTemplate.opsForValue().get(key)).isNotBlank();
        Long ttlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertThat(ttlSeconds).isBetween(1L, 120L);

        assertThat(successData(get("/products/" + productId, userToken))).isEqualTo(firstRead);
        assertMetricTags("/actuator/metrics/" + RedisProductDetailsCache.REQUESTS_METRIC,
                userToken, "hit", "miss");

        BigDecimal changedPrice = new BigDecimal("37.80");
        successData(put("/skus/" + skuId + "/price", """
                {"price":%s,"version":%d}
                """.formatted(changedPrice, skuVersion), adminToken));
        assertThat(redisTemplate.hasKey(key)).isFalse();
        JsonNode refreshed = successData(get("/products/" + productId, userToken));
        assertThat(refreshed.path("skus").get(0).path("price").decimalValue())
                .isEqualByComparingTo(changedPrice);

        successData(put("/products/" + productId + "/status", """
                {"status":"OFF_SALE"}
                """, adminToken));
        assertThat(redisTemplate.hasKey(key)).isFalse();
        assertError(get("/products/" + productId, userToken), ErrorCode.NOT_FOUND);
        assertMetricTags("/actuator/metrics/" + RedisProductDetailsCache.INVALIDATIONS_METRIC,
                adminToken, "deleted");
    }

    private void assertMetricTags(String resourcePath, String token, String... expectedValues) throws Exception {
        HttpResponse<String> response = get(resourcePath, token);
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        JsonNode tags = objectMapper.readTree(response.body()).path("availableTags");
        assertThat(tags.toString()).contains(expectedValues);
    }

    private String registerAndLogin(String username, boolean administrator) throws Exception {
        JsonNode userId = successData(post("/auth/register", """
                {"username":"%s","email":"%s@example.com","password":"secret123"}
                """.formatted(username, username), null));
        if (administrator) {
            Long roleId = jdbcTemplate.queryForObject(
                    "SELECT id FROM t_role WHERE code = ?", Long.class, RoleCodes.ADMIN);
            jdbcTemplate.update("INSERT INTO t_user_role (user_id, role_id) VALUES (?, ?)",
                    userId.asLong(), roleId);
        }
        return successData(post("/auth/login", """
                {"username":"%s","password":"secret123"}
                """.formatted(username), null)).path("accessToken").asString();
    }

    private JsonNode successData(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.path("code").asInt()).isEqualTo(Result.SUCCESS_CODE);
        return body.path("data");
    }

    private void assertError(HttpResponse<String> response, ErrorCode errorCode) {
        assertThat(response.statusCode()).isEqualTo(errorCode.getHttpStatus());
        assertThat(objectMapper.readTree(response.body()).path("code").asInt())
                .isEqualTo(errorCode.getCode());
    }

    private HttpResponse<String> get(String resourcePath, String token) throws Exception {
        return httpClient.send(request(resourcePath, token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String resourcePath, String body, String token) throws Exception {
        return httpClient.send(request(resourcePath, token)
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String resourcePath, String body, String token) throws Exception {
        return httpClient.send(request(resourcePath, token)
                        .PUT(HttpRequest.BodyPublishers.ofString(body)).build(),
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
