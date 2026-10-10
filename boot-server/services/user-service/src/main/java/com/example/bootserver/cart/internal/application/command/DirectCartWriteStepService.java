package com.example.bootserver.cart.internal.application.command;

import com.example.bootserver.cart.internal.domain.CartItemRepository;
import com.example.bootserver.cart.internal.domain.CartQuantity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Redis 原子脚本已经提供单 key 边界，写步骤不持有无意义的数据库事务。 */
@Service
@ConditionalOnProperty(prefix = "app.cart.redis", name = "enabled", havingValue = "true")
public class DirectCartWriteStepService implements CartWriteStepService {

    private final CartItemRepository repository;

    public DirectCartWriteStepService(CartItemRepository repository) {
        this.repository = repository;
    }

    @Override
    public int increase(Long userId, Long skuId, CartQuantity delta) {
        return repository.increase(userId, skuId, delta);
    }

    @Override
    public void insert(Long userId, Long skuId, CartQuantity quantity) {
        repository.insert(userId, skuId, quantity);
    }

    @Override
    public int replace(Long userId, Long itemId, CartQuantity quantity) {
        return repository.replace(userId, itemId, quantity);
    }

    @Override
    public int delete(Long userId, Long itemId) {
        return repository.delete(userId, itemId);
    }
}
