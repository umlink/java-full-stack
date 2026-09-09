package com.example.bootserver.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 用户管理分页查询参数。
 *
 * 使用可校验的查询 DTO 而非散落的 @RequestParam 约束，让缺省值与范围规则在 MVC 绑定后统一生效。
 */
public class UserPageRequest {

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
