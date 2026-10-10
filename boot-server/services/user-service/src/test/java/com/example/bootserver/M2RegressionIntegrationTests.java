package com.example.bootserver;

import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.common.result.Result;
import com.example.bootserver.config.OpenApiConfig;
import com.example.bootserver.config.ServletPathProperties;
import com.example.bootserver.identity.internal.interfaces.security.BearerAuthentication;
import com.example.bootserver.identity.internal.domain.RoleCodes;
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

/** M2 门禁：OpenAPI 四组资源与交易核心三条 DoD 的聚焦真实 HTTP 回归。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:m2-regression-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
class M2RegressionIntegrationTests {
    @LocalServerPort private int port;
    @Autowired private ServletPathProperties paths;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate db;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void openApiGroupsEveryM2OperationAndMarksBearerSecurity() throws Exception {
        HttpResponse<String> response = send(request("/v3/api-docs", null).GET().build());
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode spec = json.readTree(response.body());
        assertThat(spec.path("info").path("version").asString()).isEqualTo("M2");
        assertThat(spec.path("components").path("securitySchemes").has(OpenApiConfig.BEARER_AUTHENTICATION))
                .isTrue();

        assertOperation(spec, "/products", "get", "商品");
        assertOperation(spec, "/products", "post", "商品");
        assertOperation(spec, "/products/{id}", "get", "商品");
        assertOperation(spec, "/products/{id}/status", "put", "商品");
        assertOperation(spec, "/skus/{id}/price", "put", "商品");
        assertOperation(spec, "/stocks/{skuId}", "get", "商品");
        assertOperation(spec, "/stocks/{skuId}", "put", "商品");
        assertOperation(spec, "/cart", "get", "购物车");
        assertOperation(spec, "/cart/items", "post", "购物车");
        assertOperation(spec, "/cart/items/{id}", "put", "购物车");
        assertOperation(spec, "/cart/items/{id}", "delete", "购物车");
        assertOperation(spec, "/orders", "get", "订单");
        assertOperation(spec, "/orders", "post", "订单");
        assertOperation(spec, "/orders/{id}", "get", "订单");
        assertOperation(spec, "/orders/{id}/cancel", "post", "订单");
        assertOperation(spec, "/orders/{id}/confirm", "post", "订单");
        assertOperation(spec, "/admin/orders", "get", "订单");
        assertOperation(spec, "/admin/orders/{id}/ship", "post", "订单");
        assertOperation(spec, "/orders/{id}/pay", "post", "支付");

        assertThat(send(request("/swagger-ui/index.html", null).GET().build()).statusCode()).isEqualTo(200);
    }

    @Test
    void orderStockAndItemsRollBackTogetherWhenAnyReservationFails() throws Exception {
        Account owner = login(false);
        long availableSku = newSku();
        long missingStockSku = newSku();
        stock(availableSku, 1);

        HttpResponse<String> response = send(createOrderRequest(owner.token(), UUID.randomUUID().toString(), """
                [{"skuId":%d,"quantity":1},{"skuId":%d,"quantity":1}]
                """.formatted(availableSku, missingStockSku)));

        assertError(response, ErrorCode.CONFLICT);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM t_order WHERE user_id = ?", Integer.class, owner.id()))
                .isZero();
        assertThat(db.queryForObject("""
                SELECT COUNT(*) FROM t_order_item item
                JOIN t_order orders ON orders.id = item.order_id
                WHERE orders.user_id = ?
                """, Integer.class, owner.id())).isZero();
        assertThat(stockLocked(availableSku)).isZero();
    }

    @Test
    void sameIdempotencyKeyCreatesOneOrderAndReservesOnce() throws Exception {
        Account owner = login(false);
        long sku = newSku();
        stock(sku, 5);
        String key = UUID.randomUUID().toString();
        String items = """
                [{"skuId":%d,"quantity":2}]
                """.formatted(sku);

        long first = successfulData(send(createOrderRequest(owner.token(), key, items))).path("id").asLong();
        long second = successfulData(send(createOrderRequest(owner.token(), key, items))).path("id").asLong();

        assertThat(second).isEqualTo(first);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM t_order WHERE user_id = ?", Integer.class, owner.id()))
                .isEqualTo(1);
        assertThat(stockLocked(sku)).isEqualTo(2);
    }

    @Test
    void paymentCancellationAndShippingRejectIllegalOrderTransitions() throws Exception {
        Account owner = login(false);
        Account admin = login(true);

        long cancelled = createOrder(owner.token());
        assertSuccess(send(post("/orders/" + cancelled + "/cancel", owner.token())));
        assertError(send(post("/orders/" + cancelled + "/pay", owner.token())), ErrorCode.CONFLICT);

        long paid = createOrder(owner.token());
        assertSuccess(send(post("/orders/" + paid + "/pay", owner.token())));
        assertError(send(post("/orders/" + paid + "/cancel", owner.token())), ErrorCode.CONFLICT);
        assertError(send(post("/orders/" + paid + "/confirm", owner.token())), ErrorCode.CONFLICT);

        long created = createOrder(owner.token());
        assertError(send(post("/admin/orders/" + created + "/ship", admin.token())), ErrorCode.CONFLICT);
        assertThat(orderStatus(cancelled)).isEqualTo("CANCELLED");
        assertThat(orderStatus(paid)).isEqualTo("PAID");
        assertThat(orderStatus(created)).isEqualTo("CREATED");
    }

    private void assertOperation(JsonNode spec, String path, String method, String tag) {
        JsonNode operation = spec.path("paths").path(path).path(method);
        assertThat(operation.isMissingNode()).as(method + " " + path).isFalse();
        assertThat(operation.path("tags")).anySatisfy(value -> assertThat(value.asString()).isEqualTo(tag));
        assertThat(operation.path("security")).anySatisfy(requirement ->
                assertThat(requirement.has(OpenApiConfig.BEARER_AUTHENTICATION)).isTrue());
    }

    private long createOrder(String token) throws Exception {
        long sku = newSku();
        stock(sku, 1);
        return successfulData(send(createOrderRequest(token, UUID.randomUUID().toString(), """
                [{"skuId":%d,"quantity":1}]
                """.formatted(sku)))).path("id").asLong();
    }

    private HttpRequest createOrderRequest(String token, String key, String items) {
        return request("/orders", token).POST(HttpRequest.BodyPublishers.ofString("""
                {"idempotencyKey":"%s","items":%s}
                """.formatted(key, items))).build();
    }

    private long newSku() {
        String code = "M2-" + UUID.randomUUID();
        Long productId = db.queryForObject("SELECT MIN(id) FROM t_product WHERE status = 'ON_SALE'", Long.class);
        db.update("INSERT INTO t_sku (product_id, sku_code, price) VALUES (?, ?, 1.00)", productId, code);
        return db.queryForObject("SELECT id FROM t_sku WHERE sku_code = ?", Long.class, code);
    }

    private void stock(long skuId, long total) {
        db.update("INSERT INTO t_stock (sku_id, total_count, locked_count) VALUES (?, ?, 0)", skuId, total);
    }

    private Account login(boolean admin) throws Exception {
        String username = "m2-" + UUID.randomUUID();
        long userId = successfulData(send(request("/auth/register", null)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"username":"%s","email":"%s@example.com","password":"secret123"}
                        """.formatted(username, username))).build())).asLong();
        if (admin) {
            Long roleId = db.queryForObject("SELECT id FROM t_role WHERE code = ?", Long.class, RoleCodes.ADMIN);
            db.update("INSERT INTO t_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        }
        String token = successfulData(send(request("/auth/login", null)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"username":"%s","password":"secret123"}
                        """.formatted(username))).build())).path("accessToken").asString();
        return new Account(userId, token);
    }

    private HttpRequest post(String path, String token) {
        return request(path, token).POST(HttpRequest.BodyPublishers.noBody()).build();
    }

    private String orderStatus(long orderId) {
        return db.queryForObject("SELECT status FROM t_order WHERE id = ?", String.class, orderId);
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

    private record Account(long id, String token) {
    }
}
