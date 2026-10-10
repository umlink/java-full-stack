package com.example.bootserver.catalog.internal.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SKU 领域规则的纯单元测试：价格不变式收在实体里，
 * 校验行为不需要 Spring 上下文与数据库即可验证。
 */
class SkuTest {

    @Test
    void changePriceRejectsNegativePrice() {
        Sku sku = Sku.create(1L, "SKU-A", BigDecimal.ZERO);

        InvalidSkuPriceException exception = assertThrows(InvalidSkuPriceException.class,
                () -> sku.changePrice(new BigDecimal("-0.01")));

        assertEquals("价格不能为负数", exception.getMessage());
    }

    @Test
    void changePriceRejectsNullPrice() {
        Sku sku = Sku.create(1L, "SKU-A", BigDecimal.ZERO);

        assertThrows(InvalidSkuPriceException.class, () -> sku.changePrice(null));
    }

    @Test
    void createBuildsSkuWithValidatedPriceAndZeroVersion() {
        Sku sku = Sku.create(1L, "SKU-A", new BigDecimal("19.90"));

        assertEquals(new BigDecimal("19.90"), sku.getPrice());
        assertEquals(0, sku.getVersion());
    }
}
