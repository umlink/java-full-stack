package com.example.bootserver.cart.internal.interfaces.web;

import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.common.result.Result;
import com.example.bootserver.config.ServletPathProperties;
import com.example.bootserver.identity.internal.interfaces.security.BearerAuthentication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/** 真实 HTTP + H2 验收购物车鉴权、数量、本人隔离和并发唯一性。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:cart-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
class CartIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private ServletPathProperties servletPathProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void authenticatedUserCanAddAccumulateReadCurrentPriceReplaceAndDelete() throws Exception {
        String token = registerAndLogin("cart-happy");
        long skuId = skuId("BOOTMALL-BOOK-STD");
        assertSuccess(post("/cart/items", body(skuId, "2"), token));
        assertSuccess(post("/cart/items", body(skuId, "3"), token));
        JsonNode item = onlyItem(token);
        assertThat(item.path("skuId").asLong()).isEqualTo(skuId);
        assertThat(item.path("skuCode").asString()).isEqualTo("BOOTMALL-BOOK-STD");
        assertThat(item.path("quantity").asInt()).isEqualTo(5);
        assertThat(item.path("unitPrice").decimalValue()).isEqualByComparingTo("29.90");

        jdbcTemplate.update("UPDATE t_sku SET price = 31.25 WHERE id = ?", skuId);
        assertThat(onlyItem(token).path("unitPrice").decimalValue()).isEqualByComparingTo("31.25");

        long itemId = item.path("id").asLong();
        assertSuccess(put("/cart/items/" + itemId, "{\"quantity\":7}", token));
        assertThat(onlyItem(token).path("quantity").asInt()).isEqualTo(7);
        assertSuccess(delete("/cart/items/" + itemId, token));
        assertThat(successData(get("/cart", token)).size()).isZero();
    }

    @Test
    void everyCartRouteRequiresAuthenticationAndForeignItemsAreNotFound() throws Exception {
        String owner = registerAndLogin("cart-owner");
        String other = registerAndLogin("cart-other");
        long skuId = skuId("BOOTMALL-NOTE-A5");
        assertError(post("/cart/items", body(skuId, "1"), null), ErrorCode.UNAUTHORIZED);
        assertError(get("/cart", null), ErrorCode.UNAUTHORIZED);
        assertError(put("/cart/items/1", "{\"quantity\":2}", null), ErrorCode.UNAUTHORIZED);
        assertError(delete("/cart/items/1", null), ErrorCode.UNAUTHORIZED);

        assertSuccess(post("/cart/items", body(skuId, "1"), owner));
        long itemId = onlyItem(owner).path("id").asLong();
        assertThat(successData(get("/cart", other)).size()).isZero();
        assertError(put("/cart/items/" + itemId, "{\"quantity\":3}", other), ErrorCode.NOT_FOUND);
        assertError(delete("/cart/items/" + itemId, other), ErrorCode.NOT_FOUND);
        assertThat(onlyItem(owner).path("quantity").asInt()).isEqualTo(1);
    }

    @Test
    void invalidQuantityAndOverflowReturnParameterErrorWithoutChangingCart() throws Exception {
        String token = registerAndLogin("cart-invalid");
        long skuId = skuId("BOOTMALL-NOTE-A4");
        for (String invalid : List.of("0", "-1", "1.5", "1000")) {
            assertError(post("/cart/items", body(skuId, invalid), token), ErrorCode.PARAMETER_ERROR);
        }
        assertSuccess(post("/cart/items", body(skuId, "998"), token));
        long itemId = onlyItem(token).path("id").asLong();
        for (String invalid : List.of("0", "-1", "1.5", "1000")) {
            assertError(put("/cart/items/" + itemId, "{\"quantity\":" + invalid + "}", token),
                    ErrorCode.PARAMETER_ERROR);
        }
        assertError(post("/cart/items", body(skuId, "2"), token), ErrorCode.PARAMETER_ERROR);
        assertThat(onlyItem(token).path("quantity").asInt()).isEqualTo(998);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_cart_item WHERE sku_id = ? AND quantity = 998", Integer.class, skuId))
                .isEqualTo(1);
    }

    @Test
    void concurrentFirstAndRepeatedAddsKeepOneRowAndExactTotal() throws Exception {
        String token = registerAndLogin("cart-concurrent");
        long skuId = skuId("BOOTMALL-BOOK-STD");
        List<HttpRequest> firstWave = List.of(
                postRequest("/cart/items", body(skuId, "2"), token),
                postRequest("/cart/items", body(skuId, "3"), token));
        for (HttpResponse<String> response : sendTogether(firstWave)) {
            assertSuccess(response);
        }
        List<HttpRequest> secondWave = List.of(
                postRequest("/cart/items", body(skuId, "4"), token),
                postRequest("/cart/items", body(skuId, "5"), token));
        for (HttpResponse<String> response : sendTogether(secondWave)) {
            assertSuccess(response);
        }
        assertThat(onlyItem(token).path("quantity").asInt()).isEqualTo(14);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_user WHERE username = ?", Long.class, "cart-concurrent");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_cart_item WHERE user_id = ? AND sku_id = ?", Integer.class,
                userId, skuId)).isEqualTo(1);
    }

    @Test
    void unavailableSkuCannotBeAddedAndCartAppearsInOpenApi() throws Exception {
        String token = registerAndLogin("cart-missing");
        assertError(post("/cart/items", body(Long.MAX_VALUE, "1"), token), ErrorCode.NOT_FOUND);
        JsonNode openApi = objectMapper.readTree(get("/v3/api-docs", null).body());
        assertThat(openApi.path("paths").has("/cart/items")).isTrue();
    }

    private long skuId(String code) {
        return jdbcTemplate.queryForObject("SELECT id FROM t_sku WHERE sku_code = ?", Long.class, code);
    }

    private String body(long skuId, String quantity) {
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
