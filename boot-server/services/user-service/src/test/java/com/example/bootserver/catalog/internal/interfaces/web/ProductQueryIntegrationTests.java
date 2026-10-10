package com.example.bootserver.catalog.internal.interfaces.web;

import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.common.result.Result;
import com.example.bootserver.config.ServletPathProperties;
import com.example.bootserver.catalog.internal.domain.Product;
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

import static org.assertj.core.api.Assertions.assertThat;

/** 通过真实 HTTP 过滤器链和 H2 数据库验收商品查询的认证、分页与响应白名单。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:product-query-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
class ProductQueryIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private ServletPathProperties servletPathProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void unauthenticatedProductQueriesReturnUnifiedUnauthorizedResponse() throws Exception {
        assertUnauthorized(get("/products", null));
        assertUnauthorized(get("/products/1", null));
    }

    @Test
    void ordinaryUserCanPageOnSaleProductsAndReadSkuDetailsWithoutInternalFields() throws Exception {
        String token = registerAndLogin("product-reader");

        JsonNode firstPage = successData(get("/products?page=1&size=1", token));
        JsonNode secondPage = successData(get("/products?page=2&size=1", token));
        assertThat(firstPage.path("page").asLong()).isEqualTo(1);
        assertThat(firstPage.path("size").asLong()).isEqualTo(1);
        assertThat(firstPage.path("total").asLong()).isEqualTo(2);
        assertThat(firstPage.path("items").size()).isEqualTo(1);
        assertThat(secondPage.path("items").size()).isEqualTo(1);
        long firstId = firstPage.path("items").get(0).path("id").asLong();
        long secondId = secondPage.path("items").get(0).path("id").asLong();
        assertThat(firstId).isLessThan(secondId);
        assertThat(firstPage.path("items").get(0).path("name").asString()).isNotBlank();
        assertInternalFieldsAbsent(firstPage.path("items").get(0));

        JsonNode detail = successData(get("/products/" + firstId, token));
        assertThat(detail.path("id").asLong()).isEqualTo(firstId);
        assertThat(detail.path("name").asString()).isNotBlank();
        assertThat(detail.path("skus").size()).isGreaterThan(0);
        assertInternalFieldsAbsent(detail);
        JsonNode sku = detail.path("skus").get(0);
        assertThat(sku.path("skuCode").asString()).startsWith("BOOTMALL-");
        assertThat(sku.path("price").decimalValue()).isNotNegative();
        assertInternalFieldsAbsent(sku);
        assertThat(sku.path("productId").isMissingNode()).isTrue();
    }

    @Test
    void draftAndOffSaleProductsAreInvisibleAndTheirDetailsReturnNotFound() throws Exception {
        jdbcTemplate.update("INSERT INTO t_product (name) VALUES ('Invisible draft')");
        jdbcTemplate.update("INSERT INTO t_product (name, status) VALUES ('Invisible off sale', ?)",
                Product.STATUS_OFF_SALE);
        Long draftId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_product WHERE name = 'Invisible draft'", Long.class);
        Long offSaleId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_product WHERE name = 'Invisible off sale'", Long.class);
        String token = registerAndLogin("product-invisible-reader");

        JsonNode page = successData(get("/products?page=1&size=10", token));
        assertThat(page.path("total").asLong()).isEqualTo(2);
        assertThat(page.path("items").toString()).doesNotContain("Invisible draft", "Invisible off sale");
        assertNotFound(get("/products/" + draftId, token));
        assertNotFound(get("/products/" + offSaleId, token));
        assertNotFound(get("/products/999999", token));
    }

    @Test
    void invalidPageParametersReturnParameterError() throws Exception {
        String token = registerAndLogin("product-invalid-page-reader");
        HttpResponse<String> response = get("/products?page=0&size=1", token);
        assertThat(response.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(objectMapper.readTree(response.body()).path("code").asInt())
                .isEqualTo(ErrorCode.PARAMETER_ERROR.getCode());
    }

    private String registerAndLogin(String username) throws Exception {
        HttpResponse<String> registration = postJson("/auth/register", """
                {"username":"%s","email":"%s@example.com","password":"secret123"}
                """.formatted(username, username));
        assertThat(registration.statusCode()).isEqualTo(HttpStatus.OK.value());
        HttpResponse<String> login = postJson("/auth/login", """
                {"username":"%s","password":"secret123"}
                """.formatted(username));
        assertThat(login.statusCode()).isEqualTo(HttpStatus.OK.value());
        return objectMapper.readTree(login.body()).path("data").path("accessToken").asString();
    }

    private JsonNode successData(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.path("code").asInt()).isEqualTo(Result.SUCCESS_CODE);
        return body.path("data");
    }

    private void assertUnauthorized(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(ErrorCode.UNAUTHORIZED.getHttpStatus());
        assertThat(objectMapper.readTree(response.body()).path("code").asInt())
                .isEqualTo(ErrorCode.UNAUTHORIZED.getCode());
    }

    private void assertNotFound(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(ErrorCode.NOT_FOUND.getHttpStatus());
        assertThat(objectMapper.readTree(response.body()).path("code").asInt())
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
    }

    private void assertInternalFieldsAbsent(JsonNode response) {
        assertThat(response.path("deleted").isMissingNode()).isTrue();
        assertThat(response.path("version").isMissingNode()).isTrue();
        assertThat(response.path("createTime").isMissingNode()).isTrue();
        assertThat(response.path("updateTime").isMissingNode()).isTrue();
    }

    private HttpResponse<String> get(String resourcePath, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(resourcePath)).GET();
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String resourcePath, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(resourcePath))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String resourcePath) {
        return URI.create("http://localhost:" + port + servletPathProperties.toExternalPath(resourcePath));
    }
}
