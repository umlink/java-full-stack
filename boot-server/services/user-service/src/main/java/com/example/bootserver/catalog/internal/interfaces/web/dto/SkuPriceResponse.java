package com.example.bootserver.catalog.internal.interfaces.web.dto;

import java.math.BigDecimal;

/** 改价成功时返回新版本，供下一次改价继续使用。 */
public record SkuPriceResponse(Long id, BigDecimal price, Integer version) {
}
