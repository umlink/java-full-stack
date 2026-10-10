package com.example.bootserver.catalog.internal.infrastructure.persistence;

import com.example.bootserver.catalog.internal.infrastructure.persistence.Product;
import com.example.bootserver.catalog.internal.infrastructure.persistence.Sku;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 使用实际 H2 schema 和 MyBatis-Plus Mapper 验证商品域的持久化不变量。 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:product-sku-test;MODE=MySQL",
        "spring.sql.init.mode=always"
})
class ProductSkuMapperIntegrationTests {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    void seedProductsAndSkusAreMappedAndLinked() {
        List<Product> products = productMapper.selectList(null);
        List<Sku> skus = skuMapper.selectList(null);

        assertThat(products).extracting(Product::getName)
                .contains("BootMall 入门手册", "BootMall 练习本");
        assertThat(skus).extracting(Sku::getSkuCode)
                .contains("BOOTMALL-BOOK-STD", "BOOTMALL-NOTE-A5", "BOOTMALL-NOTE-A4");
        assertThat(products).filteredOn(product -> product.getName().startsWith("BootMall "))
                .hasSize(2).allSatisfy(product -> {
            assertThat(product.getStatus()).isEqualTo(
                    com.example.bootserver.catalog.internal.domain.Product.STATUS_ON_SALE);
            assertThat(product.getDeleted()).isZero();
            assertThat(product.getCreateTime()).isNotNull();
            assertThat(product.getUpdateTime()).isNotNull();
        });
        assertThat(skus).filteredOn(sku -> sku.getSkuCode().startsWith("BOOTMALL-"))
                .hasSize(3).allSatisfy(sku -> {
            assertThat(sku.getProductId()).isIn(products.stream().map(Product::getId).toList());
            assertThat(sku.getPrice()).isNotNegative();
            assertThat(sku.getVersion()).isZero();
            assertThat(sku.getDeleted()).isZero();
        });
    }

    @Test
    void rerunningSchemaKeepsSeedAndLearningData() throws Exception {
        Long productId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_product WHERE name = 'BootMall 入门手册'", Long.class);
        jdbcTemplate.update("INSERT INTO t_sku (product_id, sku_code, price) VALUES (?, ?, ?)",
                productId, "LOCAL-LEARNING-SKU", new BigDecimal("3.50"));
        int productCount = count("t_product");
        int skuCount = count("t_sku");

        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/schema.sql"));
        }

        assertThat(count("t_product")).isEqualTo(productCount);
        assertThat(count("t_sku")).isEqualTo(skuCount);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT price FROM t_sku WHERE sku_code = 'LOCAL-LEARNING-SKU'", BigDecimal.class))
                .isEqualByComparingTo("3.50");
    }

    @Test
    void requiredFieldsStatusPriceAndForeignKeyAreEnforced() {
        Long productId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_product WHERE name = 'BootMall 入门手册'", Long.class);

        rejects("INSERT INTO t_product (name) VALUES (NULL)");
        rejects("INSERT INTO t_product (name, status) VALUES ('bad status', 'HIDDEN')");
        rejects("INSERT INTO t_product (name, status) VALUES ('null status', NULL)");
        rejects("INSERT INTO t_product (name, deleted) VALUES ('null deleted', NULL)");
        rejects("INSERT INTO t_product (name, create_time) VALUES ('null created', NULL)");
        rejects("INSERT INTO t_product (name, update_time) VALUES ('null updated', NULL)");
        rejects("INSERT INTO t_product (name) VALUES (?)", "N".repeat(129));
        rejects("INSERT INTO t_sku (product_id, sku_code, price) VALUES (NULL, 'NULL-PRODUCT', 1.00)");
        rejects("INSERT INTO t_sku (product_id, sku_code, price) VALUES (?, NULL, 1.00)", productId);
        rejects("INSERT INTO t_sku (product_id, sku_code, price) VALUES (?, 'NULL-PRICE', NULL)", productId);
        rejects("INSERT INTO t_sku (product_id, sku_code, price) VALUES (?, 'NEGATIVE-PRICE', -0.01)", productId);
        rejects("INSERT INTO t_sku (product_id, sku_code, price, version) VALUES (?, 'NULL-VERSION', 1.00, NULL)", productId);
        rejects("INSERT INTO t_sku (product_id, sku_code, price, deleted) VALUES (?, 'NULL-DELETED', 1.00, NULL)", productId);
        rejects("INSERT INTO t_sku (product_id, sku_code, price, create_time) VALUES (?, 'NULL-CREATED', 1.00, NULL)", productId);
        rejects("INSERT INTO t_sku (product_id, sku_code, price, update_time) VALUES (?, 'NULL-UPDATED', 1.00, NULL)", productId);
        rejects("INSERT INTO t_sku (product_id, sku_code, price) VALUES (?, ?, 1.00)", productId, "S".repeat(65));
        rejects("INSERT INTO t_sku (product_id, sku_code, price) VALUES (999999, 'ORPHAN-SKU', 1.00)");
        rejects("DELETE FROM t_product WHERE id = ?", productId);
    }

    @Test
    void databaseDefaultsAndDeletedSkuCodeUniquenessAreEnforced() {
        jdbcTemplate.update("INSERT INTO t_product (name) VALUES ('Default status product')");
        Long productId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_product WHERE name = 'Default status product'", Long.class);
        Product product = productMapper.selectById(productId);
        assertThat(product.getStatus()).isEqualTo(
                com.example.bootserver.catalog.internal.domain.Product.STATUS_DRAFT);
        assertThat(product.getDeleted()).isZero();
        assertThat(product.getCreateTime()).isNotNull();
        assertThat(product.getUpdateTime()).isNotNull();

        jdbcTemplate.update("INSERT INTO t_sku (product_id, sku_code, price) VALUES (?, 'LIFETIME-UNIQUE', 0.00)",
                productId);
        Long skuId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_sku WHERE sku_code = 'LIFETIME-UNIQUE'", Long.class);
        Sku sku = skuMapper.selectById(skuId);
        assertThat(sku.getVersion()).isZero();
        assertThat(sku.getDeleted()).isZero();
        assertThat(sku.getCreateTime()).isNotNull();
        assertThat(sku.getUpdateTime()).isNotNull();

        skuMapper.deleteById(skuId);
        assertThat(skuMapper.selectById(skuId)).isNull();
        rejects("INSERT INTO t_sku (product_id, sku_code, price) VALUES (?, 'LIFETIME-UNIQUE', 1.00)",
                productId);
    }

    private void rejects(String sql, Object... parameters) {
        assertThatThrownBy(() -> jdbcTemplate.update(sql, parameters))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private int count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }
}
