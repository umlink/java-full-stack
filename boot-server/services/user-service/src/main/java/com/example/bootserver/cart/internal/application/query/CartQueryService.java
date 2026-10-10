package com.example.bootserver.cart.internal.application.query;

import com.example.bootserver.cart.internal.domain.CartItem;
import com.example.bootserver.cart.internal.domain.CartItemRepository;
import com.example.bootserver.catalog.api.CartSkuSnapshot;
import com.example.bootserver.catalog.api.CatalogQueryApi;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 组合购物车自有条目与商品模块当前展示快照。 */
@Service
public class CartQueryService {
    private final CartItemRepository repository;
    private final CatalogQueryApi catalog;

    public CartQueryService(CartItemRepository repository, CatalogQueryApi catalog) {
        this.repository = repository;
        this.catalog = catalog;
    }

    public List<CartItemView> listMyItems(Long userId) {
        List<CartItem> items = repository.listByUserId(userId);
        Set<Long> skuIds = items.stream().map(CartItem::skuId).collect(Collectors.toSet());
        Map<Long, CartSkuSnapshot> snapshots = catalog.listCartSkuSnapshots(skuIds).stream()
                .collect(Collectors.toMap(CartSkuSnapshot::skuId, Function.identity()));
        return items.stream()
                .filter(item -> snapshots.containsKey(item.skuId()))
                .map(item -> toView(item, snapshots.get(item.skuId())))
                .toList();
    }

    private CartItemView toView(CartItem item, CartSkuSnapshot snapshot) {
        return new CartItemView(item.id(), item.skuId(), snapshot.skuCode(), snapshot.productName(),
                snapshot.unitPrice(), item.quantity());
    }
}
