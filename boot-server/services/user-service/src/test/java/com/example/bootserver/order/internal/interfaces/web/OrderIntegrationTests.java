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
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/** 真实 HTTP 验证下单的身份、价格快照、条件预扣和整单回滚。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:order-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
class OrderIntegrationTests {
    @LocalServerPort private int port;
    @Autowired private ServletPathProperties paths;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate db;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void successPersistsHeaderItemsAndReservedStockWithSnapshotPrice() throws Exception {
        long first = newSku("2.50", "ON_SALE");
        long second = newSku("3.20", "ON_SALE");
        stock(first, 5);
        stock(second, 6);
        String token = login();

        JsonNode created = data(send(orderRequest(token, """
                {"items":[{"skuId":%d,"quantity":2},{"skuId":%d,"quantity":3}]}
                """.formatted(first, second))));
        long id = created.path("id").asLong();
        assertThat(created.path("status").asString()).isEqualTo("CREATED");
        assertThat(created.path("totalAmount").decimalValue()).isEqualByComparingTo("14.60");
        assertThat(created.path("orderNo").asString()).isNotBlank();
        assertThat(db.queryForObject("SELECT total_amount FROM t_order WHERE id = ?", java.math.BigDecimal.class, id))
                .isEqualByComparingTo("14.60");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM t_order_item WHERE order_id = ?", Integer.class, id))
                .isEqualTo(2);
        assertThat(db.queryForObject("SELECT price FROM t_order_item WHERE order_id = ? AND sku_id = ?",
                java.math.BigDecimal.class, id, first)).isEqualByComparingTo("2.50");
        assertThat(locked(first)).isEqualTo(2);
        assertThat(locked(second)).isEqualTo(3);
        db.update("UPDATE t_sku SET price = 8.00 WHERE id = ?", first);
        assertThat(db.queryForObject("SELECT price FROM t_order_item WHERE order_id = ? AND sku_id = ?",
                java.math.BigDecimal.class, id, first)).isEqualByComparingTo("2.50");
    }

    @Test
    void unavailableLaterSkuRollsBackOrderItemsAndEarlierReservation() throws Exception {
        long first = newSku("1.00", "ON_SALE");
        long second = newSku("1.00", "ON_SALE");
        stock(first, 2);
        stock(second, 0);
        String token = login();
        int before = countOrders();
        int beforeItems = countItems();

        assertError(send(orderRequest(token, """
                {"items":[{"skuId":%d,"quantity":1},{"skuId":%d,"quantity":1}]}
                """.formatted(first, second))), ErrorCode.CONFLICT);
        assertThat(countOrders()).isEqualTo(before);
        assertThat(countItems()).isEqualTo(beforeItems);
        assertThat(locked(first)).isZero();
        assertThat(locked(second)).isZero();
    }

    @Test
    void onlyAuthenticatedUsersCanOrderAndInvalidSkusDoNotWrite() throws Exception {
        long sku = newSku("1.00", "OFF_SALE");
        stock(sku, 5);
        assertError(send(orderRequest(null, """
                {"items":[{"skuId":%d,"quantity":1}]}
                """.formatted(sku))), ErrorCode.UNAUTHORIZED);
        String token = login();
        int before = countOrders();
        assertError(send(orderRequest(token, """
                {"items":[{"skuId":%d,"quantity":1}]}
                """.formatted(sku))), ErrorCode.NOT_FOUND);
        assertError(send(orderRequest(token, """
                {"items":[{"skuId":%d,"quantity":1},{"skuId":%d,"quantity":1}]}
                """.formatted(sku, sku))), ErrorCode.PARAMETER_ERROR);
        assertError(send(orderRequest(token, "{\"items\":[]}")), ErrorCode.PARAMETER_ERROR);
        assertError(send(orderRequest(token, "{\"items\":[null]}")), ErrorCode.PARAMETER_ERROR);
        assertThat(countOrders()).isEqualTo(before);
        assertThat(locked(sku)).isZero();
    }

    @Test
    void competingOrdersCannotReserveMoreThanTotal() throws Exception {
        long sku = newSku("1.00", "ON_SALE");
        stock(sku, 1);
        String token = login();
        int before = countOrders();
        String body = """
                {"items":[{"skuId":%d,"quantity":1}]}
                """.formatted(sku);
        CompletableFuture<HttpResponse<String>> a = client.sendAsync(orderRequest(token, body),
                HttpResponse.BodyHandlers.ofString());
        CompletableFuture<HttpResponse<String>> b = client.sendAsync(orderRequest(token, body),
                HttpResponse.BodyHandlers.ofString());
        assertThat(java.util.List.of(a.join().statusCode(), b.join().statusCode()))
                .containsExactlyInAnyOrder(200, 409);
        assertThat(countOrders()).isEqualTo(before + 1);
        assertThat(locked(sku)).isEqualTo(1);
    }

    @Test
    void sameKeyAndEquivalentItemsReplayOriginalDespitePriceAndAvailabilityChanges() throws Exception {
        long first = newSku("2.50", "ON_SALE");
        long second = newSku("3.00", "ON_SALE");
        stock(first, 1);
        stock(second, 1);
        String token = login();
        String key = "replay-" + UUID.randomUUID();
        int before = countOrders();
        int beforeItems = countItems();
        JsonNode created = data(send(orderRequest(token, key, """
                {"items":[{"skuId":%d,"quantity":1},{"skuId":%d,"quantity":1}]}
                """.formatted(first, second))));
        db.update("UPDATE t_sku SET price = 99.00 WHERE id = ?", first);
        db.update("UPDATE t_product SET status = 'OFF_SALE' WHERE id = (SELECT product_id FROM t_sku WHERE id = ?)",
                first);

        JsonNode replayed = data(send(orderRequest(token, key, """
                {"items":[{"quantity":1,"skuId":%d},{"quantity":1,"skuId":%d}]}
                """.formatted(second, first))));
        assertThat(replayed).isEqualTo(created);
        assertThat(countOrders()).isEqualTo(before + 1);
        assertThat(countItems()).isEqualTo(beforeItems + 2);
        assertThat(locked(first)).isEqualTo(1);
        assertThat(locked(second)).isEqualTo(1);
        String fingerprint = db.queryForObject("SELECT request_fingerprint FROM t_order WHERE id = ?",
                String.class, created.path("id").asLong());
        assertThat(fingerprint).matches("[0-9a-f]{64}");
    }

    @Test
    void sameKeyDifferentQuantityOrSkuConflictsAndAnotherUserCanReuseKey() throws Exception {
        long first = newSku("1.00", "ON_SALE");
        long second = newSku("1.00", "ON_SALE");
        stock(first, 4);
        stock(second, 4);
        String token = login();
        String key = "conflict-" + UUID.randomUUID();
        int before = countOrders();
        data(send(orderRequest(token, key, """
                {"items":[{"skuId":%d,"quantity":1}]}
                """.formatted(first))));
        assertError(send(orderRequest(token, key, """
                {"items":[{"skuId":%d,"quantity":2}]}
                """.formatted(first))), ErrorCode.CONFLICT);
        assertError(send(orderRequest(token, key, """
                {"items":[{"skuId":%d,"quantity":1}]}
                """.formatted(second))), ErrorCode.CONFLICT);
        assertThat(countOrders()).isEqualTo(before + 1);
        assertThat(locked(first)).isEqualTo(1);
        assertThat(locked(second)).isZero();

        data(send(orderRequest(login(), key, """
                {"items":[{"skuId":%d,"quantity":1}]}
                """.formatted(second))));
        assertThat(countOrders()).isEqualTo(before + 2);
    }

    @Test
    void concurrentSameKeySameRequestReturnsSameOrderOnce() throws Exception {
        long sku = newSku("1.00", "ON_SALE");
        stock(sku, 1);
        String token = login();
        String key = "parallel-" + UUID.randomUUID();
        String body = """
                {"items":[{"skuId":%d,"quantity":1}]}
                """.formatted(sku);
        int before = countOrders();
        CompletableFuture<HttpResponse<String>> a = client.sendAsync(orderRequest(token, key, body),
                HttpResponse.BodyHandlers.ofString());
        CompletableFuture<HttpResponse<String>> b = client.sendAsync(orderRequest(token, key, body),
                HttpResponse.BodyHandlers.ofString());
        JsonNode first = data(a.join());
        JsonNode second = data(b.join());
        assertThat(second).isEqualTo(first);
        assertThat(countOrders()).isEqualTo(before + 1);
        assertThat(locked(sku)).isEqualTo(1);
    }

    @Test
    void concurrentSameKeyDifferentRequestHasSingleWinner() throws Exception {
        long sku = newSku("1.00", "ON_SALE");
        stock(sku, 3);
        String token = login();
        String key = "parallel-conflict-" + UUID.randomUUID();
        int before = countOrders();
        CompletableFuture<HttpResponse<String>> a = client.sendAsync(orderRequest(token, key, """
                {"items":[{"skuId":%d,"quantity":1}]}
                """.formatted(sku)), HttpResponse.BodyHandlers.ofString());
        CompletableFuture<HttpResponse<String>> b = client.sendAsync(orderRequest(token, key, """
                {"items":[{"skuId":%d,"quantity":2}]}
                """.formatted(sku)), HttpResponse.BodyHandlers.ofString());
        assertThat(java.util.List.of(a.join().statusCode(), b.join().statusCode()))
                .containsExactlyInAnyOrder(200, 409);
        assertThat(countOrders()).isEqualTo(before + 1);
        long persistedQuantity = db.queryForObject("""
                SELECT quantity FROM t_order_item WHERE order_id =
                (SELECT id FROM t_order WHERE idempotency_key = ?)
                """, Long.class, key);
        assertThat(locked(sku)).isEqualTo(persistedQuantity);
    }

    @Test
    void missingOrMalformedIdempotencyKeyIsRejected() throws Exception {
        long sku = newSku("1.00", "ON_SALE");
        stock(sku, 5);
        String token = login();
        String items = "\"items\":[{\"skuId\":%d,\"quantity\":1}]".formatted(sku);
        for (String keyField : java.util.List.of("", "\"idempotencyKey\":null,",
                "\"idempotencyKey\":\"\",", "\"idempotencyKey\":\" \",",
                "\"idempotencyKey\":\"" + "x".repeat(65) + "\",",
                "\"idempotencyKey\":\"中文\",", "\"idempotencyKey\":\"a\\nb\",")) {
            assertError(send(rawOrderRequest(token, "{" + keyField + items)), ErrorCode.PARAMETER_ERROR);
        }
        assertThat(locked(sku)).isZero();
    }

    @Test
    void failedStockReservationRollsBackKeyAndCanRetryAfterRestock() throws Exception {
        long sku = newSku("1.00", "ON_SALE");
        stock(sku, 0);
        String token = login();
        String key = "restock-" + UUID.randomUUID();
        String body = """
                {"items":[{"skuId":%d,"quantity":1}]}
                """.formatted(sku);
        int before = countOrders();
        assertError(send(orderRequest(token, key, body)), ErrorCode.CONFLICT);
        assertThat(countOrders()).isEqualTo(before);
        db.update("UPDATE t_stock SET total_count = 1 WHERE sku_id = ?", sku);
        data(send(orderRequest(token, key, body)));
        assertThat(countOrders()).isEqualTo(before + 1);
        assertThat(locked(sku)).isEqualTo(1);
    }

    @Test
    void currentUserCanListOrdersAndReadDetailWithSnapshotPrice() throws Exception {
        long sku = newSku("4.25", "ON_SALE");
        stock(sku, 3);
        String token = login();
        JsonNode first = data(send(orderRequest(token, """
                {"items":[{"skuId":%d,"quantity":1}]}
                """.formatted(sku))));
        JsonNode second = data(send(orderRequest(token, """
                {"items":[{"skuId":%d,"quantity":2}]}
                """.formatted(sku))));
        db.update("UPDATE t_sku SET price = 88.00 WHERE id = ?", sku);

        JsonNode page = data(send(request("/orders?page=1&size=10", token).GET().build()));
        assertThat(page.path("total").asLong()).isEqualTo(2);
        assertThat(page.path("items").size()).isEqualTo(2);
        assertThat(page.path("items").get(0).path("id").asLong()).isEqualTo(second.path("id").asLong());
        assertThat(page.path("items").get(1).path("id").asLong()).isEqualTo(first.path("id").asLong());

        JsonNode detail = data(send(request("/orders/" + second.path("id").asLong(), token).GET().build()));
        assertThat(detail.path("status").asString()).isEqualTo("CREATED");
        assertThat(detail.path("totalAmount").decimalValue()).isEqualByComparingTo("8.50");
        assertThat(detail.path("items").size()).isEqualTo(1);
        assertThat(detail.path("items").get(0).path("skuId").asLong()).isEqualTo(sku);
        assertThat(detail.path("items").get(0).path("quantity").asInt()).isEqualTo(2);
        assertThat(detail.path("items").get(0).path("price").decimalValue()).isEqualByComparingTo("4.25");
    }

    @Test
    void orderQueriesRequireAuthenticationAndHideOtherUsersOrders() throws Exception {
        long sku = newSku("1.50", "ON_SALE");
        stock(sku, 2);
        String ownerToken = login();
        long orderId = data(send(orderRequest(ownerToken, """
                {"items":[{"skuId":%d,"quantity":1}]}
                """.formatted(sku)))).path("id").asLong();

        assertError(send(request("/orders", null).GET().build()), ErrorCode.UNAUTHORIZED);
        assertError(send(request("/orders/" + orderId, login()).GET().build()), ErrorCode.NOT_FOUND);
        assertError(send(request("/orders/999999999", ownerToken).GET().build()), ErrorCode.NOT_FOUND);
        assertError(send(request("/orders?page=0&size=10", ownerToken).GET().build()), ErrorCode.PARAMETER_ERROR);
        assertError(send(request("/orders?page=1&size=101", ownerToken).GET().build()), ErrorCode.PARAMETER_ERROR);
    }

    @Test
    void openApiContainsOrderCreationAndQueries() throws Exception {
        JsonNode spec = json.readTree(send(request("/v3/api-docs", null).GET().build()).body());
        assertThat(spec.path("paths").path("/orders").has("post")).isTrue();
        assertThat(spec.path("paths").path("/orders").has("get")).isTrue();
        assertThat(spec.path("paths").path("/orders/{id}").has("get")).isTrue();
    }

    private long newSku(String price, String status) {
        String code = "ORDER-" + UUID.randomUUID();
        Long productId = db.queryForObject("SELECT MIN(id) FROM t_product WHERE status = ?",
                Long.class, status);
        if (productId == null) {
            db.update("INSERT INTO t_product (name, status) VALUES (?, ?)", code, status);
            productId = db.queryForObject("SELECT id FROM t_product WHERE name = ?", Long.class, code);
        }
        db.update("INSERT INTO t_sku (product_id, sku_code, price) VALUES (?, ?, ?)",
                productId, code, new java.math.BigDecimal(price));
        return db.queryForObject("SELECT id FROM t_sku WHERE sku_code = ?", Long.class, code);
    }

    private void stock(long sku, long total) {
        db.update("INSERT INTO t_stock (sku_id, total_count, locked_count) VALUES (?, ?, 0)", sku, total);
    }

    private long locked(long sku) {
        return db.queryForObject("SELECT locked_count FROM t_stock WHERE sku_id = ?", Long.class, sku);
    }

    private int countOrders() {
        return db.queryForObject("SELECT COUNT(*) FROM t_order", Integer.class);
    }

    private int countItems() {
        return db.queryForObject("SELECT COUNT(*) FROM t_order_item", Integer.class);
    }

    private String login() throws Exception {
        String username = "order-" + UUID.randomUUID();
        data(send(request("/auth/register", null).POST(HttpRequest.BodyPublishers.ofString("""
                {"username":"%s","email":"%s@example.com","password":"secret123"}
                """.formatted(username, username))).build()));
        return data(send(request("/auth/login", null).POST(HttpRequest.BodyPublishers.ofString("""
                {"username":"%s","password":"secret123"}
                """.formatted(username))).build())).path("accessToken").asString();
    }

    private HttpRequest orderRequest(String token, String body) {
        return orderRequest(token, UUID.randomUUID().toString(), body);
    }

    private HttpRequest orderRequest(String token, String key, String body) {
        return rawOrderRequest(token, "{\"idempotencyKey\":\"" + key + "\"," + body.substring(1));
    }

    private HttpRequest rawOrderRequest(String token, String body) {
        return request("/orders", token).POST(HttpRequest.BodyPublishers.ofString(body)).build();
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
