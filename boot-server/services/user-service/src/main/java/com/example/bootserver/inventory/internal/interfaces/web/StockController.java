package com.example.bootserver.inventory.internal.interfaces.web;

import com.example.bootserver.common.result.Result;
import com.example.bootserver.config.OpenApiConfig;
import com.example.bootserver.inventory.internal.application.command.StockService;
import com.example.bootserver.inventory.internal.application.query.StockQueryService;
import com.example.bootserver.inventory.internal.application.query.StockView;
import com.example.bootserver.inventory.internal.domain.Stock;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员库存接口；仅设置总量，预扣与扣减留待下单用例。 */
@RestController
@RequestMapping("/stocks")
@Tag(name = "商品", description = "商品浏览、SKU 与库存管理")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTHENTICATION)
public class StockController {

    private final StockService stockService;
    private final StockQueryService stockQueryService;

    public StockController(StockService stockService, StockQueryService stockQueryService) {
        this.stockService = stockService;
        this.stockQueryService = stockQueryService;
    }

    @PutMapping("/{skuId}")
    @Operation(summary = "设置 SKU 库存总量", description = "需要 product:manage 权限；低于锁定量返回 409。")
    public Result<StockResponse> updateStock(@PathVariable Long skuId,
                                             @Valid @RequestBody UpdateStockRequest request) {
        return Result.ok(toResponse(stockService.setTotal(skuId, request.total())));
    }

    @GetMapping("/{skuId}")
    @Operation(summary = "查询 SKU 库存", description = "需要 product:manage 权限；未配置库存返回 404。")
    public Result<StockResponse> getStock(@PathVariable Long skuId) {
        StockView stock = stockQueryService.getBySkuId(skuId);
        return Result.ok(new StockResponse(stock.skuId(), stock.total(), stock.locked(), stock.available()));
    }

    private StockResponse toResponse(Stock stock) {
        return new StockResponse(stock.skuId(), stock.total(), stock.locked(), stock.available());
    }
}
