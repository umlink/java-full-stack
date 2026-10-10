package com.example.bootserver.catalog.internal.application.command;

import com.example.bootserver.common.error.BusinessException;
import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.catalog.internal.domain.CatalogRepository;
import com.example.bootserver.catalog.internal.domain.Product;
import com.example.bootserver.catalog.internal.domain.Sku;
import com.example.bootserver.catalog.internal.domain.InvalidProductStatusException;
import com.example.bootserver.catalog.internal.domain.InvalidSkuPriceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** 管理员商品写入规则；数据库约束负责并发下的最终一致性。 */
@Service
public class ProductManagementService {

    private final CatalogRepository catalogRepository;

    public ProductManagementService(CatalogRepository catalogRepository) {
        this.catalogRepository = catalogRepository;
    }

    /** 商品和所有 SKU 在同一事务创建，任一业务编码冲突时整体回滚。 */
    @Transactional
    public CreatedProduct createProduct(CreateProductCommand command) {
        Product product = catalogRepository.insertProduct(Product.draft(command.name(), command.description()));

        List<CreatedProduct.CreatedSku> createdSkus = new ArrayList<>();
        for (CreateProductCommand.CreateSkuCommand item : command.skus()) {
            Sku sku;
            try {
                sku = catalogRepository.insertSku(Sku.create(product.id(), item.skuCode(), item.price()));
            } catch (InvalidSkuPriceException exception) {
                throw new BusinessException(ErrorCode.PARAMETER_ERROR, exception.getMessage());
            }
            createdSkus.add(new CreatedProduct.CreatedSku(
                    sku.getId(), sku.getSkuCode(), sku.getVersion()));
        }
        return new CreatedProduct(product.id(), product.status(), List.copyOf(createdSkus));
    }

    /** 乐观锁插件以请求版本加入 UPDATE 条件，受影响行数为 0 即并发冲突。 */
    @Transactional
    public SkuPrice updateSkuPrice(Long id, java.math.BigDecimal price, Integer version) {
        Sku existing = catalogRepository.findSkuById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "SKU 不存在");
        }
        try {
            existing.changePrice(price);
        } catch (InvalidSkuPriceException exception) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, exception.getMessage());
        }
        Sku updated = catalogRepository.updateSkuPrice(existing, version);
        if (updated == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "SKU 价格版本已变化");
        }
        return new SkuPrice(id, updated.getPrice(), updated.getVersion());
    }

    /** 状态写入只接受本卡的上架/下架动作，创建后的草稿默认值保持独立。 */
    @Transactional
    public ProductStatus updateProductStatus(Long id, String status) {
        Product existing = catalogRepository.findProductById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        Product updated;
        try {
            updated = existing.changeStatus(status);
        } catch (InvalidProductStatusException exception) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, exception.getMessage());
        }
        if (!catalogRepository.updateProductStatus(updated)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        return new ProductStatus(id, updated.status());
    }
}
