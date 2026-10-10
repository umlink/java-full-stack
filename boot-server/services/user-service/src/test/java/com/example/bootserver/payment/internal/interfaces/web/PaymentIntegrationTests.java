package com.example.bootserver.payment.internal.interfaces.web;

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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/** 真实 HTTP 验证支付状态门、并发唯一性和跨订单/支付/库存事务回滚。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:payment-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
class PaymentIntegrationTests {
    @LocalServerPort private int port;
    @Autowired private ServletPathProperties paths;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate db;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void paymentUsesOrderAmountAndSettlesReservedStockOnce() throws Exception {
        long sku = newSku("2.50");
        stock(sku, 5);
        String token = login();
        long orderId = createOrder(token, """
                [{"skuId":%d,"quantity":2}]
                """.formatted(sku));
        db.update("UPDATE t_sku SET price = 99.00 WHERE id = ?", sku);

        JsonNode payment = data(send(payRequest(token, orderId)));

        assertThat(payment.path("orderId").asLong()).isEqualTo(orderId);
        assertThat(payment.path("payNo").asString()).startsWith("PAY");
        assertThat(payment.path("amount").decimalValue()).isEqualByComparingTo("5.00");
        assertThat(payment.path("status").asString()).isEqualTo("SUCCESS");
        assertThat(orderStatus(orderId)).isEqualTo("PAID");
        assertThat(paymentCount(orderId)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT amount FROM t_payment WHERE order_id = ?",
                java.math.BigDecimal.class, orderId)).isEqualByComparingTo("5.00");
        assertThat(stockTotal(sku)).isEqualTo(3);
        assertThat(stockLocked(sku)).isZero();
    }

    @Test
    void concurrentPaymentHasOneWinnerAndOneSettlement() throws Exception {
        long sku = newSku("1.00");
        stock(sku, 1);
        String token = login();
        long orderId = createOrder(token, """
                [{"skuId":%d,"quantity":1}]
                """.formatted(sku));

        CompletableFuture<HttpResponse<String>> first = client.sendAsync(payRequest(token, orderId),
                HttpResponse.BodyHandlers.ofString());
        CompletableFuture<HttpResponse<String>> second = client.sendAsync(payRequest(token, orderId),
                HttpResponse.BodyHandlers.ofString());

        assertThat(List.of(first.join().statusCode(), second.join().statusCode()))
                .containsExactlyInAnyOrder(200, 409);
        assertThat(paymentCount(orderId)).isEqualTo(1);
        assertThat(orderStatus(orderId)).isEqualTo("PAID");
        assertThat(stockTotal(sku)).isZero();
        assertThat(stockLocked(sku)).isZero();
    }

    @Test
    void paidAndCancelledOrdersConflictWithoutNewPayment() throws Exception {
        long paidSku = newSku("1.00");
        long cancelledSku = newSku("1.00");
        stock(paidSku, 1);
        stock(cancelledSku, 1);
        String token = login();
        long paidOrder = createOrder(token, """
                [{"skuId":%d,"quantity":1}]
                """.formatted(paidSku));
        data(send(payRequest(token, paidOrder)));
        assertError(send(payRequest(token, paidOrder)), ErrorCode.CONFLICT);
        assertThat(paymentCount(paidOrder)).isEqualTo(1);

        long cancelledOrder = createOrder(token, """
                [{"skuId":%d,"quantity":1}]
                """.formatted(cancelledSku));
        db.update("UPDATE t_order SET status = 'CANCELLED' WHERE id = ?", cancelledOrder);
        assertError(send(payRequest(token, cancelledOrder)), ErrorCode.CONFLICT);
        assertThat(paymentCount(cancelledOrder)).isZero();
        assertThat(stockTotal(cancelledSku)).isEqualTo(1);
        assertThat(stockLocked(cancelledSku)).isEqualTo(1);
    }

    @Test
    void failedLaterStockSettlementRollsBackOrderPaymentAndEarlierStock() throws Exception {
        long first = newSku("1.00");
        long second = newSku("1.00");
        stock(first, 1);
        stock(second, 1);
        String token = login();
        long orderId = createOrder(token, """
                [{"skuId":%d,"quantity":1},{"skuId":%d,"quantity":1}]
                """.formatted(first, second));
        db.update("UPDATE t_stock SET locked_count = 0 WHERE sku_id = ?", second);

        assertError(send(payRequest(token, orderId)), ErrorCode.CONFLICT);

        assertThat(orderStatus(orderId)).isEqualTo("CREATED");
        assertThat(paymentCount(orderId)).isZero();
        assertThat(stockTotal(first)).isEqualTo(1);
        assertThat(stockLocked(first)).isEqualTo(1);
        assertThat(stockTotal(second)).isEqualTo(1);
        assertThat(stockLocked(second)).isZero();
    }

    @Test
    void paymentRequiresOwnerAndAuthentication() throws Exception {
        long sku = newSku("1.00");
        stock(sku, 1);
        String owner = login();
        long orderId = createOrder(owner, """
                [{"skuId":%d,"quantity":1}]
                """.formatted(sku));

        assertError(send(payRequest(null, orderId)), ErrorCode.UNAUTHORIZED);
        assertError(send(payRequest(login(), orderId)), ErrorCode.NOT_FOUND);
        assertThat(orderStatus(orderId)).isEqualTo("CREATED");
        assertThat(paymentCount(orderId)).isZero();
    }

    @Test
    void openApiContainsPaymentEndpoint() throws Exception {
        JsonNode spec = json.readTree(send(request("/v3/api-docs", null).GET().build()).body());
        assertThat(spec.path("paths").path("/orders/{id}/pay").has("post")).isTrue();
    }

    private long newSku(String price) {
        String code = "PAY-" + UUID.randomUUID();
        Long productId = db.queryForObject("SELECT MIN(id) FROM t_product WHERE status = 'ON_SALE'", Long.class);
        db.update("INSERT INTO t_sku (product_id, sku_code, price) VALUES (?, ?, ?)",
                productId, code, new java.math.BigDecimal(price));
        return db.queryForObject("SELECT id FROM t_sku WHERE sku_code = ?", Long.class, code);
    }

    private void stock(long skuId, long total) {
        db.update("INSERT INTO t_stock (sku_id, total_count, locked_count) VALUES (?, ?, 0)", skuId, total);
    }

    private long createOrder(String token, String items) throws Exception {
        return data(send(request("/orders", token).POST(HttpRequest.BodyPublishers.ofString("""
                {"idempotencyKey":"%s","items":%s}
                """.formatted(UUID.randomUUID(), items))).build())).path("id").asLong();
    }

    private String login() throws Exception {
        String username = "payment-" + UUID.randomUUID();
        data(send(request("/auth/register", null).POST(HttpRequest.BodyPublishers.ofString("""
                {"username":"%s","email":"%s@example.com","password":"secret123"}
                """.formatted(username, username))).build()));
        return data(send(request("/auth/login", null).POST(HttpRequest.BodyPublishers.ofString("""
                {"username":"%s","password":"secret123"}
                """.formatted(username))).build())).path("accessToken").asString();
    }

    private HttpRequest payRequest(String token, long orderId) {
        return request("/orders/" + orderId + "/pay", token)
                .POST(HttpRequest.BodyPublishers.noBody()).build();
    }

    private String orderStatus(long orderId) {
        return db.queryForObject("SELECT status FROM t_order WHERE id = ?", String.class, orderId);
    }

    private int paymentCount(long orderId) {
        return db.queryForObject("SELECT COUNT(*) FROM t_payment WHERE order_id = ?", Integer.class, orderId);
    }

    private long stockTotal(long skuId) {
        return db.queryForObject("SELECT total_count FROM t_stock WHERE sku_id = ?", Long.class, skuId);
    }

    private long stockLocked(long skuId) {
        return db.queryForObject("SELECT locked_count FROM t_stock WHERE sku_id = ?", Long.class, skuId);
    }

    private HttpRequest.Builder request(String path, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port
                + paths.toExternalPath(path))).header(HttpHeaders.CONTENT_TYPE, "application/json");
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, BearerAuthentication.SCHEME_PREFIX + token);
        }
        return builder;
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode data(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(response.body());
        assertThat(body.path("code").asInt()).isEqualTo(Result.SUCCESS_CODE);
        return body.path("data");
    }

    private void assertError(HttpResponse<String> response, ErrorCode code) {
        assertThat(response.statusCode()).isEqualTo(code.getHttpStatus());
        assertThat(json.readTree(response.body()).path("code").asInt()).isEqualTo(code.getCode());
    }
}
