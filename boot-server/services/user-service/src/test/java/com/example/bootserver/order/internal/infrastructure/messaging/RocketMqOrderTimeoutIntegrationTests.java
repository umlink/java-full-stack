package com.example.bootserver.order.internal.infrastructure.messaging;

import com.example.bootserver.common.result.Result;
import com.example.bootserver.config.ServletPathProperties;
import com.example.bootserver.identity.internal.interfaces.security.BearerAuthentication;
import com.example.bootserver.order.api.OrderTimeoutEvent;
import com.example.bootserver.order.internal.application.command.OrderTimeoutDispatchTask;
import com.example.bootserver.order.internal.application.command.OrderTimeoutMetrics;
import com.example.bootserver.order.internal.application.command.OrderTimeoutPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
import java.time.Instant;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/** 真实 NameServer/Broker 验证 1 秒延时、支付竞争和 Broker 重复投递幂等。 */
@EnabledIfEnvironmentVariable(named = "BOOT_ROCKETMQ_TEST_NAMESRV_ADDR", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:rocketmq-timeout-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "app.cart.redis.enabled=false",
        "app.inventory.redis.enabled=false",
        "management.health.redis.enabled=false",
        "app.order-timeout.rocketmq.enabled=true",
        "app.order-timeout.rocketmq.namesrv-addr=${BOOT_ROCKETMQ_TEST_NAMESRV_ADDR}",
        "app.order-timeout.rocketmq.topic=${BOOT_ROCKETMQ_TEST_TOPIC:bootmall-order-timeout-test}",
        "app.order-timeout.rocketmq.producer-group=${BOOT_ROCKETMQ_TEST_PRODUCER_GROUP:bootmall-timeout-test-producer}",
        "app.order-timeout.rocketmq.consumer-group=${BOOT_ROCKETMQ_TEST_CONSUMER_GROUP:bootmall-timeout-test-consumer}",
        "app.order-timeout.rocketmq.delay-level=1",
        "app.order-timeout.rocketmq.poll-interval=PT0.1S",
        "app.order-timeout.rocketmq.retry-backoff=PT0.1S",
        "app.order-timeout.rocketmq.sending-lease=PT2S",
        "app.order-timeout.rocketmq.max-reconsume-times=2"
})
class RocketMqOrderTimeoutIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private ServletPathProperties paths;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private OrderTimeoutPublisher publisher;

    @Autowired
    private MeterRegistry meters;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void delayedMessageCancelsUnpaidIgnoresPaidAndAbsorbsBrokerDuplicates() throws Exception {
        long unpaidSku = newSku();
        long paidSku = newSku();
        stock(unpaidSku, 1);
        stock(paidSku, 1);
        String token = login();
        Instant started = Instant.now();
        long unpaidOrder = createOrder(token, unpaidSku);
        long paidOrder = createOrder(token, paidSku);
        successData(send(request("/orders/" + paidOrder + "/pay", token)
                .POST(HttpRequest.BodyPublishers.noBody()).build()));

        await(Duration.ofSeconds(25), () -> "PROCESSED".equals(outboxStatus(unpaidOrder))
                && "IGNORED".equals(outboxStatus(paidOrder)));

        assertThat(Duration.between(started, Instant.now())).isGreaterThanOrEqualTo(Duration.ofMillis(500));
        assertThat(orderStatus(unpaidOrder)).isEqualTo("CANCELLED");
        assertThat(locked(unpaidSku)).isZero();
        assertThat(total(unpaidSku)).isEqualTo(1);
        assertThat(orderStatus(paidOrder)).isEqualTo("PAID");
        assertThat(locked(paidSku)).isZero();
        assertThat(total(paidSku)).isZero();

        OrderTimeoutDispatchTask duplicate = dispatchTask(unpaidOrder);
        publisher.publish(duplicate);
        publisher.publish(duplicate);
        await(Duration.ofSeconds(25), () -> metric("consume", "duplicate") >= 2);

        assertThat(orderStatus(unpaidOrder)).isEqualTo("CANCELLED");
        assertThat(locked(unpaidSku)).isZero();
        assertThat(outboxStatus(unpaidOrder)).isEqualTo("PROCESSED");
        assertThat(metric("publish", "sent")).isGreaterThanOrEqualTo(2);
        assertThat(metric("consume", "processed")).isGreaterThanOrEqualTo(1);
        assertThat(metric("consume", "ignored")).isGreaterThanOrEqualTo(1);
    }

    private OrderTimeoutDispatchTask dispatchTask(long orderId) {
        return db.queryForObject("""
                SELECT id, event_id, business_key, order_id, payload, send_attempts
                FROM t_order_timeout_outbox WHERE order_id = ?
                """, (result, row) -> new OrderTimeoutDispatchTask(
                result.getLong("id"), result.getString("event_id"), result.getString("business_key"),
                result.getLong("order_id"), result.getString("payload"), result.getInt("send_attempts")), orderId);
    }

    private double metric(String operation, String result) {
        return meters.find(OrderTimeoutMetrics.OPERATIONS)
                .tags(OrderTimeoutMetrics.OPERATION_TAG, operation,
                        OrderTimeoutMetrics.RESULT_TAG, result)
                .counter() == null ? 0 : meters.find(OrderTimeoutMetrics.OPERATIONS)
                .tags(OrderTimeoutMetrics.OPERATION_TAG, operation,
                        OrderTimeoutMetrics.RESULT_TAG, result)
                .counter().count();
    }

    private void await(Duration timeout, BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
            Thread.sleep(100);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private String outboxStatus(long orderId) {
        return db.queryForObject("SELECT status FROM t_order_timeout_outbox WHERE order_id = ?",
                String.class, orderId);
    }

    private String orderStatus(long orderId) {
        return db.queryForObject("SELECT status FROM t_order WHERE id = ?", String.class, orderId);
    }

    private long newSku() {
        String code = "MQ-TIMEOUT-" + UUID.randomUUID();
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

    private long createOrder(String token, long skuId) throws Exception {
        return successData(send(request("/orders", token)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"idempotencyKey":"%s","items":[{"skuId":%d,"quantity":1}]}
                        """.formatted(UUID.randomUUID(), skuId))).build())).path("id").asLong();
    }

    private String login() throws Exception {
        String username = "mq-timeout-" + UUID.randomUUID();
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
