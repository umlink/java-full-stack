package com.example.bootserver.inventory.internal.infrastructure.redis;

import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.common.result.Result;
import com.example.bootserver.config.ServletPathProperties;
import com.example.bootserver.identity.internal.interfaces.security.BearerAuthentication;
import com.example.bootserver.inventory.api.InventoryCommandApi;
import com.example.bootserver.inventory.api.InventoryReservationId;
import com.example.bootserver.inventory.api.InventoryReservationResult;
import com.example.bootserver.inventory.internal.application.command.InventoryReconciliationMetrics;
import com.example.bootserver.inventory.internal.application.command.StockService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** 真实 Redis 验证 Lua 原子预占、幂等、数据库兜底和事务提交后的状态同步。 */
@EnabledIfEnvironmentVariable(named = "BOOT_REDIS_TEST_PORT", matches = "\\d+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:redis-inventory-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "app.cart.redis.enabled=false",
        "app.inventory.redis.enabled=true",
        "app.inventory.redis.ttl=PT2M",
        "app.inventory.reconciliation.enabled=true",
        "app.inventory.reconciliation.interval=PT1H",
        "app.inventory.reconciliation.batch-size=1000",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=${BOOT_REDIS_TEST_PORT}",
        "management.health.redis.enabled=true"
})
class RedisInventoryReservationIntegrationTests {

    private static final String RESERVATION_NAMESPACE_PREFIX = "order-user:";

    @LocalServerPort
    private int port;

    @Autowired
    private ServletPathProperties paths;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private InventoryCommandApi inventory;

    @Autowired
    private StockService stocks;

    @Autowired
    private InventoryReconciliationScheduler reconciliationScheduler;

    @Autowired
    private MeterRegistry meters;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    @AfterEach
    void clearRedisInventoryKeys() {
        Set<String> keys = redis.keys(RedisInventoryReservationStore.STOCK_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void fortyOrdersCompetingForTwentyItemsNeverOversell() throws Exception {
        long skuId = newSku();
        stock(skuId, 20);
        Login login = login();
        int before = countOrders();
        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            futures.add(client.sendAsync(orderRequest(login.token(), "rush-" + UUID.randomUUID(), skuId),
                    HttpResponse.BodyHandlers.ofString()));
        }

        List<Integer> statuses = futures.stream().map(CompletableFuture::join)
                .map(HttpResponse::statusCode).toList();

        assertThat(statuses).filteredOn(status -> status == HttpStatus.OK.value()).hasSize(20);
        assertThat(statuses).filteredOn(status -> status == ErrorCode.CONFLICT.getHttpStatus()).hasSize(20);
        assertThat(countOrders()).isEqualTo(before + 20);
        assertThat(locked(skuId)).isEqualTo(20);
        assertThat(redis.opsForHash().get(stockKey(skuId), RedisInventoryReservationStore.AVAILABLE_FIELD))
                .isEqualTo("0");
    }

    @Test
    void concurrentSameIdempotencyKeyReturnsOneOrderAndDeductsRedisOnce() throws Exception {
        long skuId = newSku();
        stock(skuId, 1);
        Login login = login();
        String idempotencyKey = "same-" + UUID.randomUUID();
        HttpRequest request = orderRequest(login.token(), idempotencyKey, skuId);
        CompletableFuture<HttpResponse<String>> first = client.sendAsync(request,
                HttpResponse.BodyHandlers.ofString());
        CompletableFuture<HttpResponse<String>> second = client.sendAsync(request,
                HttpResponse.BodyHandlers.ofString());

        JsonNode firstOrder = successData(first.join());
        JsonNode secondOrder = successData(second.join());

        assertThat(secondOrder).isEqualTo(firstOrder);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM t_order WHERE user_id = ? AND idempotency_key = ?",
                Integer.class, login.userId(), idempotencyKey)).isEqualTo(1);
        assertThat(locked(skuId)).isEqualTo(1);
        InventoryReservationId reservationId = reservationId(login.userId(), idempotencyKey);
        assertThat(redis.opsForHash().get(stockKey(skuId), reservationField(reservationId)))
                .isEqualTo("1|RESERVED|0");
        assertThat(redis.opsForHash().get(stockKey(skuId), RedisInventoryReservationStore.AVAILABLE_FIELD))
                .isEqualTo("0");
    }

    @Test
    void databaseRejectionCanCompensateRedisReservationIdempotently() {
        long skuId = newSku();
        stock(skuId, 1);
        InventoryReservationId reservationId = InventoryReservationId.from(
                "integration-test", UUID.randomUUID().toString());

        assertThat(inventory.preReserve(reservationId, skuId, 1))
                .isEqualTo(InventoryReservationResult.ACQUIRED);
        assertThat(redis.getExpire(stockKey(skuId), TimeUnit.SECONDS)).isBetween(1L, 120L);
        db.update("UPDATE t_stock SET locked_count = total_count WHERE sku_id = ?", skuId);
        assertThat(inventory.persistReservation(reservationId, skuId, 1)).isFalse();

        inventory.compensateReservation(reservationId, skuId, 1);
        inventory.compensateReservation(reservationId, skuId, 1);

        assertThat(redis.opsForHash().get(stockKey(skuId), RedisInventoryReservationStore.AVAILABLE_FIELD))
                .isEqualTo("1");
        assertThat(redis.opsForHash().get(stockKey(skuId), reservationField(reservationId)))
                .isEqualTo("1|RELEASED|0");

        db.update("UPDATE t_stock SET locked_count = 0 WHERE sku_id = ?", skuId);
        stocks.setTotal(skuId, 3);
        assertThat(redis.hasKey(stockKey(skuId))).isFalse();
    }

    @Test
    void paymentSettlesAndCancellationReleasesRedisAfterDatabaseCommit() throws Exception {
        long paidSku = newSku();
        long cancelledSku = newSku();
        stock(paidSku, 1);
        stock(cancelledSku, 1);
        Login login = login();
        String paidKey = "paid-" + UUID.randomUUID();
        String cancelledKey = "cancelled-" + UUID.randomUUID();
        long paidOrder = successData(send(orderRequest(login.token(), paidKey, paidSku))).path("id").asLong();
        long cancelledOrder = successData(send(orderRequest(
                login.token(), cancelledKey, cancelledSku))).path("id").asLong();

        assertSuccess(send(request("/orders/" + paidOrder + "/pay", login.token())
                .POST(HttpRequest.BodyPublishers.noBody()).build()));
        assertSuccess(send(request("/orders/" + cancelledOrder + "/cancel", login.token())
                .POST(HttpRequest.BodyPublishers.noBody()).build()));

        assertReservation(paidSku, reservationId(login.userId(), paidKey), "1|SETTLED|0", "0");
        assertReservation(cancelledSku, reservationId(login.userId(), cancelledKey), "1|RELEASED|0", "1");
        assertThat(db.queryForObject("SELECT total_count FROM t_stock WHERE sku_id = ?",
                Long.class, paidSku)).isZero();
        assertThat(locked(paidSku)).isZero();
        assertThat(db.queryForObject("SELECT total_count FROM t_stock WHERE sku_id = ?",
                Long.class, cancelledSku)).isEqualTo(1);
        assertThat(locked(cancelledSku)).isZero();

        HttpResponse<String> metric = send(request(
                "/actuator/metrics/" + RedisInventoryReservationStore.RESERVATIONS_METRIC,
                login.token()).GET().build());
        assertThat(metric.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(json.readTree(metric.body()).path("availableTags").toString())
                .contains("operation", "result", "pre_reserve", "settle", "release");
    }

    @Test
    void reconciliationInvalidatesDifferenceThenLazyRebuildConvergesIdempotently() {
        long skuId = newSku();
        stock(skuId, 7);
        db.update("UPDATE t_stock SET locked_count = 2 WHERE sku_id = ?", skuId);
        String key = stockKey(skuId);
        redis.opsForHash().put(key, RedisInventoryReservationStore.INITIALIZED_FIELD, "1");
        redis.opsForHash().put(key, RedisInventoryReservationStore.AVAILABLE_FIELD, "999");
        double repairedBefore = metric("repaired");

        reconciliationScheduler.reconcileInventories();

        assertThat(redis.hasKey(key)).isFalse();
        assertThat(metric("repaired")).isEqualTo(repairedBefore + 1);

        InventoryReservationId reservationId = InventoryReservationId.from(
                "reconciliation-test", UUID.randomUUID().toString());
        assertThat(inventory.preReserve(reservationId, skuId, 1))
                .isEqualTo(InventoryReservationResult.ACQUIRED);
        assertThat(redis.opsForHash().get(key, RedisInventoryReservationStore.AVAILABLE_FIELD))
                .isEqualTo("4");
        inventory.compensateReservation(reservationId, skuId, 1);
        assertThat(redis.opsForHash().get(key, RedisInventoryReservationStore.AVAILABLE_FIELD))
                .isEqualTo("5");

        double consistentBefore = metric("consistent");
        reconciliationScheduler.reconcileInventories();

        assertThat(redis.hasKey(key)).isTrue();
        assertThat(metric("repaired")).isEqualTo(repairedBefore + 1);
        assertThat(metric("consistent")).isGreaterThan(consistentBefore);
    }

    private void assertReservation(long skuId, InventoryReservationId reservationId,
                                   String state, String available) {
        assertThat(redis.opsForHash().get(stockKey(skuId), reservationField(reservationId))).isEqualTo(state);
        assertThat(redis.opsForHash().get(stockKey(skuId), RedisInventoryReservationStore.AVAILABLE_FIELD))
                .isEqualTo(available);
    }

    private InventoryReservationId reservationId(long userId, String idempotencyKey) {
        return InventoryReservationId.from(RESERVATION_NAMESPACE_PREFIX + userId, idempotencyKey);
    }

    private long newSku() {
        String code = "REDIS-STOCK-" + UUID.randomUUID();
        Long productId = db.queryForObject(
                "SELECT MIN(id) FROM t_product WHERE status = 'ON_SALE'", Long.class);
        db.update("INSERT INTO t_sku (product_id, sku_code, price) VALUES (?, ?, 1.00)", productId, code);
        return db.queryForObject("SELECT id FROM t_sku WHERE sku_code = ?", Long.class, code);
    }

    private void stock(long skuId, long total) {
        db.update("INSERT INTO t_stock (sku_id, total_count, locked_count) VALUES (?, ?, 0)", skuId, total);
    }

    private long locked(long skuId) {
        return db.queryForObject("SELECT locked_count FROM t_stock WHERE sku_id = ?", Long.class, skuId);
    }

    private int countOrders() {
        return db.queryForObject("SELECT COUNT(*) FROM t_order", Integer.class);
    }

    private Login login() throws Exception {
        String username = "redis-stock-" + UUID.randomUUID();
        successData(send(request("/auth/register", null)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"username":"%s","email":"%s@example.com","password":"secret123"}
                        """.formatted(username, username))).build()));
        String token = successData(send(request("/auth/login", null)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"username":"%s","password":"secret123"}
                        """.formatted(username))).build())).path("accessToken").asString();
        Long userId = db.queryForObject("SELECT id FROM t_user WHERE username = ?", Long.class, username);
        return new Login(userId, token);
    }

    private HttpRequest orderRequest(String token, String idempotencyKey, long skuId) {
        return request("/orders", token).POST(HttpRequest.BodyPublishers.ofString("""
                {"idempotencyKey":"%s","items":[{"skuId":%d,"quantity":1}]}
                """.formatted(idempotencyKey, skuId))).build();
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

    private JsonNode successData(HttpResponse<String> response) {
        assertSuccess(response);
        return json.readTree(response.body()).path("data");
    }

    private void assertSuccess(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(json.readTree(response.body()).path("code").asInt()).isEqualTo(Result.SUCCESS_CODE);
    }

    private String stockKey(long skuId) {
        return RedisInventoryReservationStore.STOCK_KEY_PREFIX + "{" + skuId + "}";
    }

    private String reservationField(InventoryReservationId reservationId) {
        return RedisInventoryReservationStore.RESERVATION_FIELD_PREFIX + reservationId.value();
    }

    private double metric(String result) {
        Counter counter = meters.find(InventoryReconciliationMetrics.OPERATIONS)
                .tag(InventoryReconciliationMetrics.RESULT_TAG, result)
                .counter();
        return counter == null ? 0 : counter.count();
    }

    private record Login(long userId, String token) {
    }
}
