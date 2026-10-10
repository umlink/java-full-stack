package com.example.bootserver.catalog.internal.interfaces.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 商品列表页码和页大小；由 MVC 绑定后统一校验。 */
public class ProductPageRequest {

    @Min(value = 1, message = "页码必须大于 0")
    private long page = 1;

    @Min(value = 1, message = "每页数量必须大于 0")
    @Max(value = 100, message = "每页数量不能超过 100")
    private long size = 10;

    public long getPage() {
        return page;
    }

    public void setPage(long page) {
        this.page = page;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}
