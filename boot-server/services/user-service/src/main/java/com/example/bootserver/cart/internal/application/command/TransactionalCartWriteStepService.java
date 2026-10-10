package com.example.bootserver.cart.internal.application.command;

import com.example.bootserver.cart.internal.domain.CartItemRepository;
import com.example.bootserver.cart.internal.domain.CartQuantity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 数据库回退模式让每个写步骤独立提交，唯一键冲突回滚后才能安全重试累加。 */
@Service
@ConditionalOnProperty(prefix = "app.cart.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class TransactionalCartWriteStepService implements CartWriteStepService {

    private final CartItemRepository repository;

    public TransactionalCartWriteStepService(CartItemRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public int increase(Long userId, Long skuId, CartQuantity delta) {
        return repository.increase(userId, skuId, delta);
    }

    @Override
    @Transactional
    public void insert(Long userId, Long skuId, CartQuantity quantity) {
        repository.insert(userId, skuId, quantity);
    }

    @Override
    @Transactional
    public int replace(Long userId, Long itemId, CartQuantity quantity) {
        return repository.replace(userId, itemId, quantity);
    }

    @Override
    @Transactional
    public int delete(Long userId, Long itemId) {
        return repository.delete(userId, itemId);
    }
}
