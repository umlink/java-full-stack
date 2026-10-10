package com.example.bootserver.cart.internal.interfaces.web;

import com.example.bootserver.cart.internal.application.command.CartService;
import com.example.bootserver.cart.internal.application.query.CartItemView;
import com.example.bootserver.cart.internal.application.query.CartQueryService;
import com.example.bootserver.common.result.Result;
import com.example.bootserver.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 购物车 HTTP 入口；用户身份只取有效 JWT，不接受请求体指定所有者。 */
@RestController
@RequestMapping("/cart")
@Tag(name = "购物车", description = "当前登录用户的购物车")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTHENTICATION)
public class CartController {

    private final CartService cartService;
    private final CartQueryService cartQueries;

    public CartController(CartService cartService, CartQueryService cartQueries) {
        this.cartService = cartService;
        this.cartQueries = cartQueries;
    }

    @PostMapping("/items")
    @Operation(summary = "加入购物车", description = "quantity 为正增量；同一 SKU 原子累加，最多 999 件。")
    public Result<Void> addItem(@AuthenticationPrincipal Long userId,
                                @Valid @RequestBody AddCartItemRequest request) {
        cartService.addItem(userId, request.skuId(), request.quantity().intValueExact());
        return Result.ok();
    }

    @GetMapping
    @Operation(summary = "查看我的购物车", description = "返回当前 SKU 价格，不锁价、不预留库存。")
    public Result<List<CartItemResponse>> listMyItems(@AuthenticationPrincipal Long userId) {
        return Result.ok(cartQueries.listMyItems(userId).stream().map(this::toResponse).toList());
    }

    @PutMapping("/items/{id}")
    @Operation(summary = "设置购物车条目数量", description = "quantity 为 1 至 999 的绝对值；非本人条目返回 404。")
    public Result<Void> updateItemQuantity(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                           @Valid @RequestBody UpdateCartItemRequest request) {
        cartService.updateItemQuantity(userId, id, request.quantity().intValueExact());
        return Result.ok();
    }

    @DeleteMapping("/items/{id}")
    @Operation(summary = "移除购物车条目", description = "非本人条目返回 404。")
    public Result<Void> deleteItem(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        cartService.deleteItem(userId, id);
        return Result.ok();
    }

    private CartItemResponse toResponse(CartItemView entry) {
        return new CartItemResponse(entry.id(), entry.skuId(), entry.skuCode(), entry.productName(),
                entry.unitPrice(), entry.quantity());
    }
}
