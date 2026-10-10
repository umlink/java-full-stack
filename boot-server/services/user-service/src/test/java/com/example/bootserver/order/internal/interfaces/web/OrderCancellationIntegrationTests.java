package com.example.bootserver.order.internal.interfaces.web;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 真实 HTTP 验证本人取消、非法状态和订单加库存的事务回滚。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:order-cancel-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
class OrderCancellationIntegrationTests {
    @LocalServerPort private int port;
    @Autowired private ServletPathProperties paths;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate db;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void cancellationReleasesReservedStockWithoutChangingTotal() throws Exception {
        long sku = newSku();
        stock(sku, 5);
        String token = login();
        long orderId = createOrder(token, """
                [{"skuId":%d,"quantity":2}]
                """.formatted(sku));

        assertSuccess(send(cancelRequest(token, orderId)));

        assertThat(orderStatus(orderId)).isEqualTo("CANCELLED");
        assertThat(stockTotal(sku)).isEqualTo(5);
        assertThat(stockLocked(sku)).isZero();
    }

    @Test
    void paidAndAlreadyCancelledOrdersCannotBeCancelled() throws Exception {
        long paidSku = newSku();
        long cancelledSku = newSku();
        stock(paidSku, 1);
        stock(cancelledSku, 1);
        String token = login();
        long paidOrder = createOrder(token, """
                [{"skuId":%d,"quantity":1}]
                """.formatted(paidSku));
        assertSuccess(send(request("/orders/" + paidOrder + "/pay", token)
                .POST(HttpRequest.BodyPublishers.noBody()).build()));
        assertError(send(cancelRequest(token, paidOrder)), ErrorCode.CONFLICT);
        assertThat(stockTotal(paidSku)).isZero();
        assertThat(stockLocked(paidSku)).isZero();

        long cancelledOrder = createOrder(token, """
                [{"skuId":%d,"quantity":1}]
                """.formatted(cancelledSku));
        assertSuccess(send(cancelRequest(token, cancelledOrder)));
        assertError(send(cancelRequest(token, cancelledOrder)), ErrorCode.CONFLICT);
        assertThat(stockTotal(cancelledSku)).isEqualTo(1);
        assertThat(stockLocked(cancelledSku)).isZero();
    }

    @Test
    void failedLaterReleaseRollsBackOrderAndEarlierStock() throws Exception {
        long first = newSku();
        long second = newSku();
        stock(first, 1);
        stock(second, 1);
        String token = login();
        long orderId = createOrder(token, """
                [{"skuId":%d,"quantity":1},{"skuId":%d,"quantity":1}]
                """.formatted(first, second));
        db.update("UPDATE t_stock SET locked_count = 0 WHERE sku_id = ?", second);

        assertError(send(cancelRequest(token, orderId)), ErrorCode.CONFLICT);

        assertThat(orderStatus(orderId)).isEqualTo("CREATED");
        assertThat(stockLocked(first)).isEqualTo(1);
        assertThat(stockLocked(second)).isZero();
    }

    @Test
    void cancellationRequiresOwnerAndAuthentication() throws Exception {
        long sku = newSku();
        stock(sku, 1);
        String owner = login();
        long orderId = createOrder(owner, """
                [{"skuId":%d,"quantity":1}]
                """.formatted(sku));

        assertError(send(cancelRequest(null, orderId)), ErrorCode.UNAUTHORIZED);
        assertError(send(cancelRequest(login(), orderId)), ErrorCode.NOT_FOUND);
        assertThat(orderStatus(orderId)).isEqualTo("CREATED");
        assertThat(stockLocked(sku)).isEqualTo(1);
    }

    @Test
    void openApiContainsCancellationEndpoint() throws Exception {
        JsonNode spec = json.readTree(send(request("/v3/api-docs", null).GET().build()).body());
        assertThat(spec.path("paths").path("/orders/{id}/cancel").has("post")).isTrue();
    }

    private long newSku() {
        String code = "CANCEL-" + UUID.randomUUID();
        Long productId = db.queryForObject("SELECT MIN(id) FROM t_product WHERE status = 'ON_SALE'", Long.class);
        db.update("INSERT INTO t_sku (product_id, sku_code, price) VALUES (?, ?, 1.00)", productId, code);
        return db.queryForObject("SELECT id FROM t_sku WHERE sku_code = ?", Long.class, code);
    }

    private void stock(long skuId, long total) {
        db.update("INSERT INTO t_stock (sku_id, total_count, locked_count) VALUES (?, ?, 0)", skuId, total);
    }

    private long createOrder(String token, String items) throws Exception {
        HttpResponse<String> response = send(request("/orders", token)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"idempotencyKey":"%s","items":%s}
                        """.formatted(UUID.randomUUID(), items))).build());
        return successfulData(response).path("id").asLong();
    }

    private String login() throws Exception {
        String username = "cancel-" + UUID.randomUUID();
        successfulData(send(request("/auth/register", null).POST(HttpRequest.BodyPublishers.ofString("""
                {"username":"%s","email":"%s@example.com","password":"secret123"}
                """.formatted(username, username))).build()));
        return successfulData(send(request("/auth/login", null).POST(HttpRequest.BodyPublishers.ofString("""
                {"username":"%s","password":"secret123"}
                """.formatted(username))).build())).path("accessToken").asString();
    }

    private HttpRequest cancelRequest(String token, long orderId) {
        return request("/orders/" + orderId + "/cancel", token)
                .POST(HttpRequest.BodyPublishers.noBody()).build();
    }

    private String orderStatus(long orderId) {
        return db.queryForObject("SELECT status FROM t_order WHERE id = ?", String.class, orderId);
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

    private void assertSuccess(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).path("code").asInt()).isEqualTo(Result.SUCCESS_CODE);
    }

    private JsonNode successfulData(HttpResponse<String> response) {
        assertSuccess(response);
        return json.readTree(response.body()).path("data");
    }

    private void assertError(HttpResponse<String> response, ErrorCode code) {
        assertThat(response.statusCode()).isEqualTo(code.getHttpStatus());
        assertThat(json.readTree(response.body()).path("code").asInt()).isEqualTo(code.getCode());
    }
}
