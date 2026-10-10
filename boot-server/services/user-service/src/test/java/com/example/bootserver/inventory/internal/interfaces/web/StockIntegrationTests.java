package com.example.bootserver.inventory.internal.interfaces.web;

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
import org.springframework.http.HttpStatus;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 真实 HTTP + H2 验收库存权限、首次配置、条件更新与并发首次设置。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:stock-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
class StockIntegrationTests {

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
    void stockManagementRequiresAuthenticationAndProductPermission() throws Exception {
        long skuId = newSku();
        String ordinaryToken = registerAndLogin(false);

        assertError(get(skuId, null), ErrorCode.UNAUTHORIZED);
        assertError(put(skuId, "{\"total\":10}", null), ErrorCode.UNAUTHORIZED);
        assertError(get(skuId, ordinaryToken), ErrorCode.FORBIDDEN);
        assertError(put(skuId, "{\"total\":10}", ordinaryToken), ErrorCode.FORBIDDEN);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_stock WHERE sku_id = ?", Integer.class, skuId)).isZero();
    }

    @Test
    void administratorSetsStockAndCannotReduceTotalBelowLocked() throws Exception {
        long skuId = newSku();
        String token = registerAndLogin(true);

        JsonNode first = successData(put(skuId, "{\"total\":10,\"locked\":9,\"available\":1}", token));
        assertCounts(first, 10, 0, 10);
        assertCounts(successData(get(skuId, token)), 10, 0, 10);
        assertThat(jdbcTemplate.update(
                "UPDATE t_stock SET locked_count = 7 WHERE sku_id = ?", skuId)).isEqualTo(1);

        assertError(put(skuId, "{\"total\":6}", token), ErrorCode.CONFLICT);
        assertCounts(successData(get(skuId, token)), 10, 7, 3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT locked_count FROM t_stock WHERE sku_id = ?", Long.class, skuId)).isEqualTo(7);
        assertCounts(successData(put(skuId, "{\"total\":7}", token)), 7, 7, 0);
        assertCounts(successData(put(skuId, "{\"total\":7}", token)), 7, 7, 0);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE t_stock SET locked_count = 8 WHERE sku_id = ?", skuId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void missingSkuUnconfiguredStockAndInvalidTotalHaveDefinedErrors() throws Exception {
        long skuId = newSku();
        String token = registerAndLogin(true);

        assertError(get(skuId, token), ErrorCode.NOT_FOUND);
        assertError(put(Long.MAX_VALUE, "{\"total\":1}", token), ErrorCode.NOT_FOUND);
        assertError(put(skuId, "{\"total\":-1}", token), ErrorCode.PARAMETER_ERROR);
        assertError(put(skuId, "{\"total\":null}", token), ErrorCode.PARAMETER_ERROR);
        assertError(put(skuId, "{\"total\":1.5}", token), ErrorCode.PARAMETER_ERROR);
        assertError(put(skuId, "{\"total\":\"2\"}", token), ErrorCode.PARAMETER_ERROR);
        assertError(put(skuId, "{\"total\":9223372036854775808}", token), ErrorCode.PARAMETER_ERROR);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_stock WHERE sku_id = ?", Integer.class, skuId)).isZero();
    }

    @Test
    void concurrentFirstSettingsKeepOneRowAndValidCounts() throws Exception {
        long skuId = newSku();
        String token = registerAndLogin(true);

        CompletableFuture<HttpResponse<String>> first = httpClient.sendAsync(
                putRequest(skuId, "{\"total\":10}", token), HttpResponse.BodyHandlers.ofString());
        CompletableFuture<HttpResponse<String>> second = httpClient.sendAsync(
                putRequest(skuId, "{\"total\":20}", token), HttpResponse.BodyHandlers.ofString());
        List<HttpResponse<String>> responses = List.of(first.join(), second.join());

        assertThat(responses).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(200));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_stock WHERE sku_id = ?", Integer.class, skuId)).isEqualTo(1);
        long total = jdbcTemplate.queryForObject(
                "SELECT total_count FROM t_stock WHERE sku_id = ?", Long.class, skuId);
        assertThat(total).isIn(10L, 20L);
        assertCounts(successData(get(skuId, token)), total, 0, total);
    }

    @Test
    void openApiListsBothStockOperations() throws Exception {
        JsonNode paths = successDataFromOpenApi(getResource("/v3/api-docs", null)).path("paths");
        assertThat(paths.path("/stocks/{skuId}").has("put")).isTrue();
        assertThat(paths.path("/stocks/{skuId}").has("get")).isTrue();
    }

    private long newSku() {
        Long productId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_product WHERE name = 'BootMall 入门手册'", Long.class);
        String code = "STOCK-TEST-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO t_sku (product_id, sku_code, price) VALUES (?, ?, 1.00)", productId, code);
        return jdbcTemplate.queryForObject("SELECT id FROM t_sku WHERE sku_code = ?", Long.class, code);
    }

    private String registerAndLogin(boolean administrator) throws Exception {
        String username = "stock-" + UUID.randomUUID();
        JsonNode userId = successData(send(request("/auth/register", null)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"username":"%s","email":"%s@example.com","password":"secret123"}
                        """.formatted(username, username))).build()));
        if (administrator) {
            Long roleId = jdbcTemplate.queryForObject(
                    "SELECT id FROM t_role WHERE code = ?", Long.class, RoleCodes.ADMIN);
            jdbcTemplate.update("INSERT INTO t_user_role (user_id, role_id) VALUES (?, ?)", userId.asLong(), roleId);
        }
        JsonNode login = successData(send(request("/auth/login", null)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"username":"%s","password":"secret123"}
                        """.formatted(username))).build()));
        return login.path("accessToken").asString();
    }

    private void assertCounts(JsonNode data, long total, long locked, long available) {
        assertThat(data.path("total").asLong()).isEqualTo(total);
        assertThat(data.path("locked").asLong()).isEqualTo(locked);
        assertThat(data.path("available").asLong()).isEqualTo(available);
    }

    private JsonNode successData(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.path("code").asInt()).isEqualTo(Result.SUCCESS_CODE);
        return body.path("data");
    }

    private JsonNode successDataFromOpenApi(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        return objectMapper.readTree(response.body());
    }

    private void assertError(HttpResponse<String> response, ErrorCode errorCode) {
        assertThat(response.statusCode()).isEqualTo(errorCode.getHttpStatus());
        assertThat(objectMapper.readTree(response.body()).path("code").asInt()).isEqualTo(errorCode.getCode());
    }

    private HttpResponse<String> get(long skuId, String token) throws Exception {
        return getResource("/stocks/" + skuId, token);
    }

    private HttpResponse<String> getResource(String path, String token) throws Exception {
        return send(request(path, token).GET().build());
    }

    private HttpResponse<String> put(long skuId, String body, String token) throws Exception {
        return send(putRequest(skuId, body, token));
    }

    private HttpRequest putRequest(long skuId, String body, String token) {
        return request("/stocks/" + skuId, token).PUT(HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(
                "http://localhost:" + port + servletPathProperties.toExternalPath(path)))
                .header(HttpHeaders.CONTENT_TYPE, "application/json");
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, BearerAuthentication.SCHEME_PREFIX + token);
        }
        return builder;
    }
}
