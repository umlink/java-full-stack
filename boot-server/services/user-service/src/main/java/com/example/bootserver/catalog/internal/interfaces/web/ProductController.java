package com.example.bootserver.catalog.internal.interfaces.web;

import com.example.bootserver.common.result.Result;
import com.example.bootserver.config.OpenApiConfig;
import com.example.bootserver.catalog.internal.interfaces.web.dto.ProductDetailResponse;
import com.example.bootserver.catalog.internal.interfaces.web.dto.CreateProductRequest;
import com.example.bootserver.catalog.internal.interfaces.web.dto.CreatedProductResponse;
import com.example.bootserver.catalog.internal.interfaces.web.dto.ProductPageRequest;
import com.example.bootserver.catalog.internal.interfaces.web.dto.ProductPageResponse;
import com.example.bootserver.catalog.internal.interfaces.web.dto.ProductStatusResponse;
import com.example.bootserver.catalog.internal.interfaces.web.dto.UpdateProductStatusRequest;
import com.example.bootserver.catalog.internal.application.command.ProductManagementService;
import com.example.bootserver.catalog.internal.application.command.CreateProductCommand;
import com.example.bootserver.catalog.internal.application.command.CreatedProduct;
import com.example.bootserver.catalog.internal.application.command.ProductStatus;
import com.example.bootserver.catalog.internal.application.query.ProductDetails;
import com.example.bootserver.catalog.internal.application.query.ProductPage;
import com.example.bootserver.catalog.internal.application.query.ProductQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 商品接口；读取仅需有效 JWT，写入要求商品管理权限。 */
@RestController
@RequestMapping("/products")
@Tag(name = "商品", description = "商品浏览、SKU 与库存管理")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTHENTICATION)
public class ProductController {

    private final ProductQueryService productQueryService;
    private final ProductManagementService productManagementService;

    public ProductController(ProductQueryService productQueryService,
                             ProductManagementService productManagementService) {
        this.productQueryService = productQueryService;
        this.productManagementService = productManagementService;
    }

    /** 分页列表：只返回上架商品，按商品 ID 升序。 */
    @GetMapping
    @Operation(summary = "分页浏览上架商品", description = "任意有效 JWT 均可访问；页码从 1 开始，每页最多 100 条。")
    public Result<ProductPageResponse> listProductsByPage(@Valid @ModelAttribute ProductPageRequest request) {
        ProductPage page = productQueryService.listProductsByPage(request.getPage(), request.getSize());
        return Result.ok(new ProductPageResponse(page.page(), page.size(), page.total(), page.items().stream()
                .map(item -> new com.example.bootserver.catalog.internal.interfaces.web.dto.ProductSummaryResponse(
                        item.id(), item.name(), item.description()))
                .toList()));
    }

    /** 商品详情：未上架或不存在时返回 404。 */
    @GetMapping("/{id}")
    @Operation(summary = "查看上架商品详情", description = "任意有效 JWT 均可访问；返回商品及其未删除的 SKU。")
    public Result<ProductDetailResponse> getProductById(@PathVariable Long id) {
        ProductDetails details = productQueryService.getProductById(id);
        return Result.ok(new ProductDetailResponse(details.id(), details.name(), details.description(),
                details.skus().stream()
                        .map(sku -> new com.example.bootserver.catalog.internal.interfaces.web.dto.SkuResponse(
                                sku.id(), sku.skuCode(), sku.price()))
                        .toList()));
    }

    /** 管理员创建草稿商品与 SKU；数据库事务保证整体创建。 */
    @PostMapping
    @Operation(summary = "创建商品与 SKU", description = "需要 product:manage 权限；商品初始状态为草稿。")
    public Result<CreatedProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        CreatedProduct created = productManagementService.createProduct(new CreateProductCommand(
                request.name(), request.description(), request.skus().stream()
                        .map(sku -> new CreateProductCommand.CreateSkuCommand(sku.skuCode(), sku.price()))
                        .toList()));
        return Result.ok(new CreatedProductResponse(created.id(), created.status(), created.skus().stream()
                .map(sku -> new CreatedProductResponse.CreatedSkuResponse(
                        sku.id(), sku.skuCode(), sku.version()))
                .toList()));
    }

    /** 管理员上架或下架商品，C 端查询只展示上架状态。 */
    @PutMapping("/{id}/status")
    @Operation(summary = "商品上架或下架", description = "需要 product:manage 权限；只接受 ON_SALE 或 OFF_SALE。")
    public Result<ProductStatusResponse> updateProductStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateProductStatusRequest request) {
        ProductStatus status = productManagementService.updateProductStatus(id, request.status());
        return Result.ok(new ProductStatusResponse(status.id(), status.status()));
    }
}
