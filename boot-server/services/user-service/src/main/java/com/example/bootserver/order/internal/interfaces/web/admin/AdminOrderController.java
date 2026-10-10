package com.example.bootserver.order.internal.interfaces.web.admin;

import com.example.bootserver.common.result.Result;
import com.example.bootserver.config.OpenApiConfig;
import com.example.bootserver.order.internal.application.query.AdminOrderPage;
import com.example.bootserver.order.internal.application.command.OrderFulfillmentService;
import com.example.bootserver.order.internal.application.query.OrderQueryService;
import com.example.bootserver.order.internal.interfaces.web.OrderPageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 具备 order:manage 权限的后台订单查询与发货入口。 */
@RestController
@RequestMapping("/admin/orders")
@Tag(name = "订单", description = "本人订单生命周期与后台履约管理")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTHENTICATION)
public class AdminOrderController {
    private final OrderQueryService queries;
    private final OrderFulfillmentService fulfillment;

    public AdminOrderController(OrderQueryService queries, OrderFulfillmentService fulfillment) {
        this.queries = queries;
        this.fulfillment = fulfillment;
    }

    @GetMapping
    @Operation(summary = "分页查询全部订单", description = "按创建时间和订单 ID 倒序，包含订单所有者标识。")
    public Result<AdminOrderPageResponse> listOrders(@Valid @ModelAttribute OrderPageRequest request) {
        AdminOrderPage page = queries.listAllOrders(request.getPage(), request.getSize());
        return Result.ok(new AdminOrderPageResponse(page.page(), page.size(), page.total(), page.items().stream()
                .map(order -> new AdminOrderSummaryResponse(order.id(), order.orderNo(), order.userId(),
                        order.status(), order.totalAmount(), order.createTime()))
                .toList()));
    }

    @PostMapping("/{id}/ship")
    @Operation(summary = "订单发货", description = "仅 PAID 订单可迁移为 SHIPPED。")
    public Result<Void> shipOrder(@PathVariable Long id) {
        fulfillment.ship(id);
        return Result.ok();
    }
}
