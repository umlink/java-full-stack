package com.example.bootserver.cart.internal.application.command;

import com.example.bootserver.cart.internal.domain.CartItemRepository;
import com.example.bootserver.cart.internal.domain.CartQuantity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 每个数据库写步骤独立提交；插入冲突回滚后，调用方才能在新事务中安全重试累加。 */
@Service
public class CartWriteStepService {

    private final CartItemRepository repository;

    public CartWriteStepService(CartItemRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public int increase(Long userId, Long skuId, CartQuantity delta) {
        return repository.increase(userId, skuId, delta);
    }

    @Transactional
    public void insert(Long userId, Long skuId, CartQuantity quantity) {
        repository.insert(userId, skuId, quantity);
    }

    @Transactional
    public int replace(Long userId, Long itemId, CartQuantity quantity) {
        return repository.replace(userId, itemId, quantity);
    }

    @Transactional
    public int delete(Long userId, Long itemId) {
        return repository.delete(userId, itemId);
    }
}
