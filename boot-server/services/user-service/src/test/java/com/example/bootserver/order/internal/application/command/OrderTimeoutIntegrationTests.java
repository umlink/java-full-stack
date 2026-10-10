package com.example.bootserver.order.internal.application.command;

import com.example.bootserver.common.result.Result;
import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.config.ServletPathProperties;
import com.example.bootserver.identity.internal.interfaces.security.BearerAuthentication;
import com.example.bootserver.order.api.OrderTimeoutEvent;
import com.example.bootserver.order.internal.infrastructure.messaging.OrderTimeoutOutboxDispatcher;
import com.example.bootserver.order.internal.infrastructure.messaging.RocketMqOrderTimeoutProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 不依赖 MQ 服务验证 Outbox 原子落库、消费幂等、事务回滚和发送失败终态。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:order-timeout-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "app.order-timeout.rocketmq.enabled=false"
})
class OrderTimeoutIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private ServletPathProperties paths;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private OrderTimeoutService timeouts;

    @Autowired
    private OrderTimeoutDispatchStateService dispatchStates;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void createdOrderAndOutboxCommitTogetherThenTimeoutReleasesStockOnce() throws Exception {
        long skuId = newSku();
        stock(skuId, 2);
        String token = login();
        long orderId = createOrder(token, "[{\"skuId\":" + skuId + ",\"quantity\":2}]");
        OrderTimeoutEvent event = event(orderId);

        assertThat(outboxStatus(orderId)).isEqualTo("PENDING");
        assertThat(locked(skuId)).isEqualTo(2);

        assertThat(timeouts.consume(event)).isEqualTo(OrderTimeoutHandlingResult.PROCESSED);
        assertThat(timeouts.consume(event)).isEqualTo(OrderTimeoutHandlingResult.DUPLICATE);

        assertThat(orderStatus(orderId)).isEqualTo("CANCELLED");
        assertThat(locked(skuId)).isZero();
        assertThat(outboxStatus(orderId)).isEqualTo("PROCESSED");
    }

    @Test
    void paidOrderIgnoresTimeoutWithoutRestoringSoldStock() throws Exception {
        long skuId = newSku();
        stock(skuId, 1);
        String token = login();
        long orderId = createOrder(token, "[{\"skuId\":" + skuId + ",\"quantity\":1}]");
        OrderTimeoutEvent event = event(orderId);
        successData(send(request("/orders/" + orderId + "/pay", token)
                .POST(HttpRequest.BodyPublishers.noBody()).build()));

        assertThat(timeouts.consume(event)).isEqualTo(OrderTimeoutHandlingResult.IGNORED);

        assertThat(orderStatus(orderId)).isEqualTo("PAID");
        assertThat(total(skuId)).isZero();
        assertThat(locked(skuId)).isZero();
        assertThat(outboxStatus(orderId)).isEqualTo("IGNORED");
    }

    @Test
    void failedStockReleaseRollsBackOrderAndOutboxForBrokerRetry() throws Exception {
        long first = newSku();
        long second = newSku();
        stock(first, 1);
        stock(second, 1);
        long orderId = createOrder(login(), "[{\"skuId\":" + first
                + ",\"quantity\":1},{\"skuId\":" + second + ",\"quantity\":1}]");
        OrderTimeoutEvent event = event(orderId);
        db.update("UPDATE t_stock SET locked_count = 0 WHERE sku_id = ?", second);

        assertThatThrownBy(() -> timeouts.consume(event))
                .isInstanceOf(RuntimeException.class);

        assertThat(orderStatus(orderId)).isEqualTo("CREATED");
        assertThat(locked(first)).isEqualTo(1);
        assertThat(locked(second)).isZero();
        assertThat(outboxStatus(orderId)).isEqualTo("PENDING");
    }

    @Test
    void repeatedPublishFailuresReachDeadWithoutAffectingOrder() throws Exception {
        long skuId = newSku();
        stock(skuId, 1);
        long orderId = createOrder(login(), "[{\"skuId\":" + skuId + ",\"quantity\":1}]");
        RocketMqOrderTimeoutProperties properties = new RocketMqOrderTimeoutProperties();
        properties.setSendingLease(Duration.ofSeconds(1));
        properties.setBatchSize(50);
        properties.setMaxSendAttempts(2);
        properties.setRetryBackoff(Duration.ofMillis(1));
        OrderTimeoutPublisher failing = task -> {
            throw new IllegalStateException("injected publish failure");
        };
        OrderTimeoutOutboxDispatcher dispatcher = new OrderTimeoutOutboxDispatcher(
                dispatchStates, failing, properties, new SimpleMeterRegistry());

        dispatcher.dispatchReady();
        db.update("UPDATE t_order_timeout_outbox SET next_attempt_time = CURRENT_TIMESTAMP WHERE order_id = ?",
                orderId);
        dispatcher.dispatchReady();

        assertThat(outboxStatus(orderId)).isEqualTo("DEAD");
        assertThat(db.queryForObject(
                "SELECT send_attempts FROM t_order_timeout_outbox WHERE order_id = ?",
                Integer.class, orderId)).isEqualTo(2);
        assertThat(db.queryForObject(
                "SELECT last_error FROM t_order_timeout_outbox WHERE order_id = ?",
                String.class, orderId)).contains("injected publish failure");
        assertThat(orderStatus(orderId)).isEqualTo("CREATED");
        assertThat(locked(skuId)).isEqualTo(1);
    }

    @Test
    void failedOrderTransactionDoesNotLeaveOrphanOutbox() throws Exception {
        long skuId = newSku();
        stock(skuId, 0);
        String token = login();
        int beforeOrders = db.queryForObject("SELECT COUNT(*) FROM t_order", Integer.class);
        int beforeOutbox = db.queryForObject("SELECT COUNT(*) FROM t_order_timeout_outbox", Integer.class);

        HttpResponse<String> response = send(request("/orders", token)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"idempotencyKey":"%s","items":[{"skuId":%d,"quantity":1}]}
                        """.formatted(UUID.randomUUID(), skuId))).build());

        assertThat(response.statusCode()).isEqualTo(ErrorCode.CONFLICT.getHttpStatus());
        assertThat(json.readTree(response.body()).path("code").asInt()).isEqualTo(ErrorCode.CONFLICT.getCode());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM t_order", Integer.class)).isEqualTo(beforeOrders);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM t_order_timeout_outbox", Integer.class))
                .isEqualTo(beforeOutbox);
    }

    private OrderTimeoutEvent event(long orderId) {
        String payload = db.queryForObject(
                "SELECT payload FROM t_order_timeout_outbox WHERE order_id = ?", String.class, orderId);
        return json.readValue(payload, OrderTimeoutEvent.class);
    }

    private String outboxStatus(long orderId) {
        return db.queryForObject("SELECT status FROM t_order_timeout_outbox WHERE order_id = ?",
                String.class, orderId);
    }

    private String orderStatus(long orderId) {
        return db.queryForObject("SELECT status FROM t_order WHERE id = ?", String.class, orderId);
    }

    private long newSku() {
        String code = "TIMEOUT-" + UUID.randomUUID();
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

    private long total(long skuId) {
        return db.queryForObject("SELECT total_count FROM t_stock WHERE sku_id = ?", Long.class, skuId);
    }

    private long createOrder(String token, String items) throws Exception {
        return successData(send(request("/orders", token)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"idempotencyKey":"%s","items":%s}
                        """.formatted(UUID.randomUUID(), items))).build())).path("id").asLong();
    }

    private String login() throws Exception {
        String username = "timeout-" + UUID.randomUUID();
        successData(send(request("/auth/register", null)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"username":"%s","email":"%s@example.com","password":"secret123"}
                        """.formatted(username, username))).build()));
        return successData(send(request("/auth/login", null)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"username":"%s","password":"secret123"}
                        """.formatted(username))).build())).path("accessToken").asString();
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
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(response.body());
        assertThat(body.path("code").asInt()).isEqualTo(Result.SUCCESS_CODE);
        return body.path("data");
    }
}
