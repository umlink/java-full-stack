package com.example.bootserver.cart.internal.infrastructure.redis;

import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.common.result.Result;
import com.example.bootserver.config.ServletPathProperties;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** 真实 Redis + HTTP 验证迁移、用户隔离、原子写、滑动 TTL 与指标。 */
@EnabledIfEnvironmentVariable(named = "BOOT_REDIS_TEST_PORT", matches = "\\d+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:redis-cart-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "app.cart.redis.enabled=true",
        "app.cart.redis.ttl=PT2M",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=${BOOT_REDIS_TEST_PORT}",
        "management.health.redis.enabled=true"
})
class RedisCartIntegrationTests {

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
    void clearRedisCartKeys() {
        Set<String> keys = redisTemplate.keys(RedisCartItemRepository.CART_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void firstReadMigratesLegacyRowsOnceAndPreservesItemIdentity() throws Exception {
        String username = "redis-cart-migration";
        String token = registerAndLogin(username);
        Long userId = userId(username);
        Long skuId = skuId("BOOTMALL-BOOK-STD");
        jdbcTemplate.update("INSERT INTO t_cart_item (user_id, sku_id, quantity) VALUES (?, ?, ?)",
                userId, skuId, 6);
        Long legacyItemId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_cart_item WHERE user_id = ? AND sku_id = ?", Long.class, userId, skuId);

        JsonNode item = onlyItem(token);
        assertThat(item.path("id").asLong()).isEqualTo(legacyItemId);
        assertThat(item.path("quantity").asInt()).isEqualTo(6);
        assertThat(redisTemplate.opsForHash().get(cartKey(userId), skuId.toString()))
                .isEqualTo(legacyItemId + ":6");
        assertThat(redisTemplate.opsForValue().get(migrationKey(userId))).isEqualTo("complete");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_cart_item WHERE user_id = ?", Integer.class, userId)).isZero();

        assertThat(onlyItem(token).path("id").asLong()).isEqualTo(legacyItemId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_cart_item WHERE user_id = ?", Integer.class, userId)).isZero();
    }

    @Test
    void concurrentAddsUpdateDeleteAndUserIsolationKeepRedisInvariants() throws Exception {
        String ownerName = "redis-cart-owner";
        String ownerToken = registerAndLogin(ownerName);
        String otherToken = registerAndLogin("redis-cart-other");
        Long ownerId = userId(ownerName);
        long skuId = skuId("BOOTMALL-NOTE-A5");

        List<HttpRequest> firstWave = List.of(
                postRequest("/cart/items", body(skuId, 2), ownerToken),
                postRequest("/cart/items", body(skuId, 3), ownerToken));
        for (HttpResponse<String> response : sendTogether(firstWave)) {
            assertSuccess(response);
        }
        List<HttpRequest> secondWave = List.of(
                postRequest("/cart/items", body(skuId, 4), ownerToken),
                postRequest("/cart/items", body(skuId, 5), ownerToken));
        for (HttpResponse<String> response : sendTogether(secondWave)) {
            assertSuccess(response);
        }

        JsonNode item = onlyItem(ownerToken);
        assertThat(item.path("quantity").asInt()).isEqualTo(14);
        assertThat(redisTemplate.opsForHash().size(cartKey(ownerId))).isEqualTo(1);
        assertThat(successData(get("/cart", otherToken)).size()).isZero();

        long itemId = item.path("id").asLong();
        assertError(put("/cart/items/" + itemId, "{\"quantity\":2}", otherToken), ErrorCode.NOT_FOUND);
        assertSuccess(put("/cart/items/" + itemId, "{\"quantity\":999}", ownerToken));
        assertError(post("/cart/items", body(skuId, 1), ownerToken), ErrorCode.PARAMETER_ERROR);
        assertThat(onlyItem(ownerToken).path("quantity").asInt()).isEqualTo(999);
        assertSuccess(delete("/cart/items/" + itemId, ownerToken));
        assertThat(successData(get("/cart", ownerToken)).size()).isZero();
    }

    @Test
    void cartKeysUseConfiguredTtlAndOperationsAreObservable() throws Exception {
        String username = "redis-cart-metrics";
        String token = registerAndLogin(username);
        Long userId = userId(username);
        assertSuccess(post("/cart/items", body(skuId("BOOTMALL-NOTE-A4"), 1), token));
        assertThat(successData(get("/cart", token)).size()).isEqualTo(1);

        assertThat(redisTemplate.getExpire(cartKey(userId), TimeUnit.SECONDS)).isBetween(1L, 120L);
        assertThat(redisTemplate.getExpire(migrationKey(userId), TimeUnit.SECONDS)).isBetween(1L, 120L);

        HttpResponse<String> metric = get("/actuator/metrics/" + RedisCartItemRepository.OPERATIONS_METRIC, token);
        assertThat(metric.statusCode()).isEqualTo(HttpStatus.OK.value());
        JsonNode tags = objectMapper.readTree(metric.body()).path("availableTags");
        assertThat(tags.toString()).contains("operation", "migration", "insert", "list", "success");
        assertThat(get("/actuator/health", token).statusCode()).isEqualTo(HttpStatus.OK.value());
    }

    private Long userId(String username) {
        return jdbcTemplate.queryForObject("SELECT id FROM t_user WHERE username = ?", Long.class, username);
    }

    private long skuId(String code) {
        return jdbcTemplate.queryForObject("SELECT id FROM t_sku WHERE sku_code = ?", Long.class, code);
    }

    private String cartKey(Long userId) {
        return RedisCartItemRepository.CART_KEY_PREFIX + userId;
    }

    private String migrationKey(Long userId) {
        return RedisCartItemRepository.MIGRATION_KEY_PREFIX + userId;
    }

    private String body(long skuId, int quantity) {
        return "{\"skuId\":" + skuId + ",\"quantity\":" + quantity + "}";
    }

    private String registerAndLogin(String username) throws Exception {
        assertSuccess(post("/auth/register", """
                {"username":"%s","email":"%s@example.com","password":"secret123"}
                """.formatted(username, username), null));
        return successData(post("/auth/login", """
                {"username":"%s","password":"secret123"}
                """.formatted(username), null)).path("accessToken").asString();
    }

    private JsonNode onlyItem(String token) throws Exception {
        JsonNode items = successData(get("/cart", token));
        assertThat(items.size()).isEqualTo(1);
        return items.get(0);
    }

    private JsonNode successData(HttpResponse<String> response) {
        assertSuccess(response);
        return objectMapper.readTree(response.body()).path("data");
    }

    private void assertSuccess(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(objectMapper.readTree(response.body()).path("code").asInt()).isEqualTo(Result.SUCCESS_CODE);
    }

    private void assertError(HttpResponse<String> response, ErrorCode errorCode) {
        assertThat(response.statusCode()).isEqualTo(errorCode.getHttpStatus());
        assertThat(objectMapper.readTree(response.body()).path("code").asInt()).isEqualTo(errorCode.getCode());
    }

    private List<HttpResponse<String>> sendTogether(List<HttpRequest> requests) {
        List<CompletableFuture<HttpResponse<String>>> futures = requests.stream()
                .map(request -> httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())).toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private HttpResponse<String> get(String resourcePath, String token) throws Exception {
        return httpClient.send(request(resourcePath, token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String resourcePath, String body, String token) throws Exception {
        return httpClient.send(postRequest(resourcePath, body, token), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest postRequest(String resourcePath, String body, String token) {
        return request(resourcePath, token).POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private HttpResponse<String> put(String resourcePath, String body, String token) throws Exception {
        return httpClient.send(request(resourcePath, token)
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String resourcePath, String token) throws Exception {
        return httpClient.send(request(resourcePath, token).DELETE().build(), HttpResponse.BodyHandlers.ofString());
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
