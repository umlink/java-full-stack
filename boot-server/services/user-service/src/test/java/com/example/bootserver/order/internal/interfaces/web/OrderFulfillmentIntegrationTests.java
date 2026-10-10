package com.example.bootserver.order.internal.interfaces.web;

import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.common.result.Result;
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

/** 真实 HTTP 验证后台权限、全量分页和 PAID→SHIPPED→DONE 状态链。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:order-fulfillment-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
class OrderFulfillmentIntegrationTests {
    @LocalServerPort private int port;
    @Autowired private ServletPathProperties paths;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate db;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void paidOrderCanBeShippedAndConfirmedByOwner() throws Exception {
        Account owner = login(false);
        Account admin = login(true);
        long orderId = createPaidOrder(owner.token());

        JsonNode page = successfulData(send(request("/admin/orders?page=1&size=100", admin.token()).GET().build()));
        JsonNode listed = null;
        for (JsonNode item : page.path("items")) {
            if (item.path("id").asLong() == orderId) {
                listed = item;
                break;
            }
        }
        assertThat(listed).isNotNull();
        assertThat(listed.path("userId").asLong()).isEqualTo(owner.id());
        assertThat(listed.path("status").asString()).isEqualTo("PAID");

        assertSuccess(send(post("/admin/orders/" + orderId + "/ship", admin.token())));
        assertThat(orderStatus(orderId)).isEqualTo("SHIPPED");
        assertSuccess(send(post("/orders/" + orderId + "/confirm", owner.token())));
        assertThat(orderStatus(orderId)).isEqualTo("DONE");
    }

    @Test
    void outOfOrderAndRepeatedTransitionsAreRejected() throws Exception {
        Account owner = login(false);
        Account admin = login(true);
        long created = createOrder(owner.token());
        assertError(send(post("/admin/orders/" + created + "/ship", admin.token())), ErrorCode.CONFLICT);
        assertError(send(post("/orders/" + created + "/confirm", owner.token())), ErrorCode.CONFLICT);

        assertSuccess(send(post("/orders/" + created + "/pay", owner.token())));
        assertError(send(post("/orders/" + created + "/confirm", owner.token())), ErrorCode.CONFLICT);
        assertSuccess(send(post("/admin/orders/" + created + "/ship", admin.token())));
        assertError(send(post("/admin/orders/" + created + "/ship", admin.token())), ErrorCode.CONFLICT);
        assertSuccess(send(post("/orders/" + created + "/confirm", owner.token())));
        assertError(send(post("/orders/" + created + "/confirm", owner.token())), ErrorCode.CONFLICT);
        assertError(send(post("/admin/orders/" + created + "/ship", admin.token())), ErrorCode.CONFLICT);
        assertThat(orderStatus(created)).isEqualTo("DONE");
    }

    @Test
    void ordinaryUserCannotReadOrShipAdminOrders() throws Exception {
        Account user = login(false);
        long orderId = createPaidOrder(user.token());

        assertError(send(request("/admin/orders", null).GET().build()), ErrorCode.UNAUTHORIZED);
        assertError(send(request("/admin/orders", user.token()).GET().build()), ErrorCode.FORBIDDEN);
        assertError(send(post("/admin/orders/" + orderId + "/ship", null)), ErrorCode.UNAUTHORIZED);
        assertError(send(post("/admin/orders/" + orderId + "/ship", user.token())), ErrorCode.FORBIDDEN);
        assertThat(orderStatus(orderId)).isEqualTo("PAID");
    }

    @Test
    void onlyOrderOwnerCanConfirmReceipt() throws Exception {
        Account owner = login(false);
        Account other = login(false);
        Account admin = login(true);
        long orderId = createPaidOrder(owner.token());
        assertSuccess(send(post("/admin/orders/" + orderId + "/ship", admin.token())));

        assertError(send(post("/orders/" + orderId + "/confirm", null)), ErrorCode.UNAUTHORIZED);
        assertError(send(post("/orders/" + orderId + "/confirm", other.token())), ErrorCode.NOT_FOUND);
        assertThat(orderStatus(orderId)).isEqualTo("SHIPPED");
    }

    @Test
    void openApiContainsAdminAndConfirmationEndpoints() throws Exception {
        JsonNode spec = json.readTree(send(request("/v3/api-docs", null).GET().build()).body());
        assertThat(spec.path("paths").path("/admin/orders").has("get")).isTrue();
        assertThat(spec.path("paths").path("/admin/orders/{id}/ship").has("post")).isTrue();
        assertThat(spec.path("paths").path("/orders/{id}/confirm").has("post")).isTrue();
    }

    private long createPaidOrder(String token) throws Exception {
        long orderId = createOrder(token);
        assertSuccess(send(post("/orders/" + orderId + "/pay", token)));
        return orderId;
    }

    private long createOrder(String token) throws Exception {
        long sku = newSku();
        db.update("INSERT INTO t_stock (sku_id, total_count, locked_count) VALUES (?, 1, 0)", sku);
        return successfulData(send(request("/orders", token).POST(HttpRequest.BodyPublishers.ofString("""
                {"idempotencyKey":"%s","items":[{"skuId":%d,"quantity":1}]}
                """.formatted(UUID.randomUUID(), sku))).build())).path("id").asLong();
    }

    private long newSku() {
        String code = "FULFILL-" + UUID.randomUUID();
        Long productId = db.queryForObject("SELECT MIN(id) FROM t_product WHERE status = 'ON_SALE'", Long.class);
        db.update("INSERT INTO t_sku (product_id, sku_code, price) VALUES (?, ?, 1.00)", productId, code);
        return db.queryForObject("SELECT id FROM t_sku WHERE sku_code = ?", Long.class, code);
    }

    private Account login(boolean admin) throws Exception {
        String username = "fulfill-" + UUID.randomUUID();
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
