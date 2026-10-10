package com.example.bootserver.catalog.internal.interfaces.web;

import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.common.result.Result;
import com.example.bootserver.config.ServletPathProperties;
import com.example.bootserver.catalog.internal.domain.Product;
import com.example.bootserver.identity.internal.interfaces.security.BearerAuthentication;
import com.example.bootserver.identity.internal.domain.RoleCodes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/** 通过真实 HTTP 和数据库验收商品管理权限、创建事务、状态变化与并发改价。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:product-management-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
class ProductManagementIntegrationTests {

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
    void ordinaryUserIsForbiddenFromEveryManagementWrite() throws Exception {
        String token = registerAndLogin("ordinary-product-manager", false);
        Long productId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_product WHERE name = 'BootMall 入门手册'", Long.class);
        Long skuId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_sku WHERE sku_code = 'BOOTMALL-BOOK-STD'", Long.class);

        assertUnauthorized(post("/products", createBody("Unauthorized product", "UNAUTHORIZED-SKU"), null));
        assertUnauthorized(put("/skus/" + skuId + "/price", """
                {"price":25.00,"version":0}
                """, null));
        assertUnauthorized(put("/products/" + productId + "/status", """
                {"status":"OFF_SALE"}
                """, null));
        assertForbidden(post("/products", createBody("Forbidden product", "FORBIDDEN-SKU"), token));
        assertForbidden(put("/skus/" + skuId + "/price", """
                {"price":25.00,"version":0}
                """, token));
        assertForbidden(put("/products/" + productId + "/status", """
                {"status":"OFF_SALE"}
                """, token));
    }

    @Test
    void administratorCreatesProductAndStatusControlsCustomerVisibility() throws Exception {
        String token = registerAndLogin("admin-product-creator", true);
        JsonNode created = successData(post("/products", """
                {"id":999999,"name":"Managed product","description":"Manager created product",
                 "status":"ON_SALE","deleted":1,
                 "skus":[{"id":999999,"productId":999999,"skuCode":"MANAGED-SKU",
                          "price":19.90,"version":9,"deleted":1}]}
                """, token));
        long productId = created.path("id").asLong();
        assertThat(productId).isNotEqualTo(999999);
        assertThat(created.path("status").asString()).isEqualTo(Product.STATUS_DRAFT);
        assertThat(created.path("skus").size()).isEqualTo(1);
        assertThat(created.path("skus").get(0).path("version").asInt()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted FROM t_product WHERE id = ?", Integer.class, productId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted FROM t_sku WHERE product_id = ?", Integer.class, productId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_sku WHERE product_id = ?", Integer.class, productId)).isEqualTo(1);
        assertNotFound(get("/products/" + productId, token));

        JsonNode onSale = successData(put("/products/" + productId + "/status", """
                {"status":"ON_SALE"}
                """, token));
        assertThat(onSale.path("status").asString()).isEqualTo(Product.STATUS_ON_SALE);
        JsonNode detail = successData(get("/products/" + productId, token));
        assertThat(detail.path("skus").get(0).path("skuCode").asString()).isEqualTo("MANAGED-SKU");
        assertThat(get("/products?page=1&size=100", token).body()).contains("Managed product");

        JsonNode offSale = successData(put("/products/" + productId + "/status", """
                {"status":"OFF_SALE"}
                """, token));
        assertThat(offSale.path("status").asString()).isEqualTo(Product.STATUS_OFF_SALE);
        assertNotFound(get("/products/" + productId, token));
        assertThat(get("/products?page=1&size=100", token).body()).doesNotContain("Managed product");
    }

    @Test
    void invalidPriceAndDuplicateSkuRollBackProductCreation() throws Exception {
        String token = registerAndLogin("admin-invalid-product", true);
        HttpResponse<String> negativePrice = post("/products", """
                {"name":"Negative price product","skus":[{"skuCode":"NEGATIVE-ADMIN-SKU","price":-0.01}]}
                """, token);
        assertParameterError(negativePrice);
        Long existingSkuId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_sku WHERE sku_code = 'BOOTMALL-BOOK-STD'", Long.class);
        assertParameterError(put("/skus/" + existingSkuId + "/price", """
                {"price":-1.00,"version":0}
                """, token));
        Long existingProductId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_product WHERE name = 'BootMall 入门手册'", Long.class);
        assertParameterError(put("/products/" + existingProductId + "/status", """
                {"status":"HIDDEN"}
                """, token));

        HttpResponse<String> duplicateSku = post("/products", """
                {"name":"Duplicate code product","skus":[
                  {"skuCode":"DUPLICATE-ADMIN-SKU","price":10.00},
                  {"skuCode":"DUPLICATE-ADMIN-SKU","price":11.00}]}
                """, token);
        assertError(duplicateSku, ErrorCode.CONFLICT);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_product WHERE name = 'Duplicate code product'", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_sku WHERE sku_code = 'DUPLICATE-ADMIN-SKU'", Integer.class)).isZero();
    }

    @Test
    void concurrentPriceUpdatesWithSameVersionHaveOneWinnerAndOneConflict() throws Exception {
        String token = registerAndLogin("admin-concurrent-price", true);
        JsonNode created = successData(post("/products", createBody("Concurrent price product", "CONCURRENT-PRICE-SKU"), token));
        long skuId = created.path("skus").get(0).path("id").asLong();

        HttpRequest firstRequest = putRequest("/skus/" + skuId + "/price", """
                {"price":20.00,"version":0}
                """, token);
        HttpRequest secondRequest = putRequest("/skus/" + skuId + "/price", """
                {"price":30.00,"version":0}
                """, token);
        CompletableFuture<HttpResponse<String>> first = httpClient.sendAsync(
                firstRequest, HttpResponse.BodyHandlers.ofString());
        CompletableFuture<HttpResponse<String>> second = httpClient.sendAsync(
                secondRequest, HttpResponse.BodyHandlers.ofString());
        List<HttpResponse<String>> responses = List.of(first.join(), second.join());

        assertThat(responses).extracting(HttpResponse::statusCode)
                .containsExactlyInAnyOrder(HttpStatus.OK.value(), ErrorCode.CONFLICT.getHttpStatus());
        HttpResponse<String> conflict = responses.stream()
                .filter(response -> response.statusCode() == ErrorCode.CONFLICT.getHttpStatus())
                .findFirst().orElseThrow();
        assertError(conflict, ErrorCode.CONFLICT);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM t_sku WHERE id = ?", Integer.class, skuId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT price FROM t_sku WHERE id = ?", BigDecimal.class, skuId))
                .isIn(new BigDecimal("20.00"), new BigDecimal("30.00"));

        HttpResponse<String> staleRetry = put("/skus/" + skuId + "/price", """
                {"price":40.00,"version":0}
                """, token);
        assertError(staleRetry, ErrorCode.CONFLICT);
        JsonNode nextVersion = successData(put("/skus/" + skuId + "/price", """
                {"price":40.00,"version":1}
                """, token));
        assertThat(nextVersion.path("version").asInt()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT price FROM t_sku WHERE id = ?", BigDecimal.class, skuId))
                .isEqualByComparingTo("40.00");
    }

    private String createBody(String name, String skuCode) {
        return """
                {"name":"%s","description":"Manager created product","skus":[{"skuCode":"%s","price":19.90}]}
                """.formatted(name, skuCode);
    }

    private String registerAndLogin(String username, boolean administrator) throws Exception {
        HttpResponse<String> registration = post("/auth/register", """
                {"username":"%s","email":"%s@example.com","password":"secret123"}
                """.formatted(username, username), null);
        JsonNode registrationData = successData(registration);
        if (administrator) {
            Long roleId = jdbcTemplate.queryForObject(
                    "SELECT id FROM t_role WHERE code = ?", Long.class, RoleCodes.ADMIN);
            jdbcTemplate.update("INSERT INTO t_user_role (user_id, role_id) VALUES (?, ?)",
                    registrationData.asLong(), roleId);
        }
        HttpResponse<String> login = post("/auth/login", """
                {"username":"%s","password":"secret123"}
                """.formatted(username), null);
        return successData(login).path("accessToken").asString();
    }

    private JsonNode successData(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.path("code").asInt()).isEqualTo(Result.SUCCESS_CODE);
        return body.path("data");
    }

    private void assertForbidden(HttpResponse<String> response) {
        assertError(response, ErrorCode.FORBIDDEN);
    }

    private void assertUnauthorized(HttpResponse<String> response) {
        assertError(response, ErrorCode.UNAUTHORIZED);
    }

    private void assertNotFound(HttpResponse<String> response) {
        assertError(response, ErrorCode.NOT_FOUND);
    }

    private void assertParameterError(HttpResponse<String> response) {
        assertError(response, ErrorCode.PARAMETER_ERROR);
    }

    private void assertError(HttpResponse<String> response, ErrorCode errorCode) {
        assertThat(response.statusCode()).isEqualTo(errorCode.getHttpStatus());
        assertThat(objectMapper.readTree(response.body()).path("code").asInt())
                .isEqualTo(errorCode.getCode());
    }

    private HttpResponse<String> get(String resourcePath, String token) throws Exception {
        return httpClient.send(request(resourcePath, token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String resourcePath, String body, String token) throws Exception {
        return httpClient.send(request(resourcePath, token)
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String resourcePath, String body, String token) throws Exception {
        return httpClient.send(putRequest(resourcePath, body, token), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest putRequest(String resourcePath, String body, String token) {
        return request(resourcePath, token)
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build();
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
