package com.example.bootserver.cart.internal.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.bootserver.cart.internal.domain.CartItem;
import com.example.bootserver.cart.internal.domain.CartItemRepository;
import com.example.bootserver.cart.internal.domain.CartQuantity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 将购物车端口映射到 MyBatis；商品表只通过只读查询参与可售判断与现价投影。 */
@Repository
@ConditionalOnProperty(prefix = "app.cart.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class MyBatisCartItemRepository implements CartItemRepository {

    private final CartItemMapper mapper;

    public MyBatisCartItemRepository(CartItemMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int increase(Long userId, Long skuId, CartQuantity delta) {
        return mapper.increase(userId, skuId, delta.value(), CartQuantity.MAX);
    }

    @Override
    public void insert(Long userId, Long skuId, CartQuantity quantity) {
        CartItemEntity entity = new CartItemEntity();
        entity.setUserId(userId);
        entity.setSkuId(skuId);
        entity.setQuantity(quantity.value());
        mapper.insert(entity);
    }

    @Override
    public int replace(Long userId, Long itemId, CartQuantity quantity) {
        return mapper.replace(userId, itemId, quantity.value());
    }

    @Override
    public int delete(Long userId, Long itemId) {
        return mapper.deleteOwned(userId, itemId);
    }

    @Override
    public List<CartItem> listByUserId(Long userId) {
        return mapper.selectList(new LambdaQueryWrapper<CartItemEntity>()
                        .eq(CartItemEntity::getUserId, userId)
                        .orderByAsc(CartItemEntity::getId)).stream()
                .map(entity -> new CartItem(entity.getId(), entity.getSkuId(), entity.getQuantity()))
                .toList();
    }
}
