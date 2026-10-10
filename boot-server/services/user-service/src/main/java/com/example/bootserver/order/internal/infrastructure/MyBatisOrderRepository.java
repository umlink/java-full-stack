package com.example.bootserver.order.internal.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.bootserver.order.internal.domain.ExistingOrder;
import com.example.bootserver.order.internal.domain.IdempotencyKey;
import com.example.bootserver.order.internal.domain.Order;
import com.example.bootserver.order.internal.domain.OrderLine;
import com.example.bootserver.order.internal.domain.OrderRepository;
import com.example.bootserver.order.internal.domain.OrderRequestFingerprint;
import com.example.bootserver.order.internal.domain.OrderStatus;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 把订单聚合转换成两个表的持久化对象。 */
@Repository
public class MyBatisOrderRepository implements OrderRepository {
    private final OrderMapper orderMapper;
    private final OrderItemMapper itemMapper;

    public MyBatisOrderRepository(OrderMapper orderMapper, OrderItemMapper itemMapper) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
    }

    @Override
    public Long insert(Order order) {
        OrderEntity entity = new OrderEntity();
        entity.setOrderNo(order.orderNo());
        entity.setUserId(order.userId());
        entity.setIdempotencyKey(order.idempotencyKey().value());
        entity.setRequestFingerprint(order.fingerprint().value());
        entity.setStatus(order.status().name());
        entity.setTotalAmount(order.totalAmount());
        orderMapper.insert(entity);
        for (OrderLine line : order.lines()) {
            OrderItemEntity item = new OrderItemEntity();
            item.setOrderId(entity.getId());
            item.setSkuId(line.skuId());
            item.setQuantity(line.quantity());
            item.setPrice(line.unitPrice());
            itemMapper.insert(item);
        }
        return entity.getId();
    }

    @Override
    public Optional<ExistingOrder> getByUserIdAndIdempotencyKey(Long userId, IdempotencyKey key) {
        OrderEntity entity = orderMapper.selectOne(new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getUserId, userId)
                .eq(OrderEntity::getIdempotencyKey, key.value()));
        return Optional.ofNullable(entity).map(found -> new ExistingOrder(found.getId(), found.getOrderNo(),
                OrderStatus.valueOf(found.getStatus()), found.getTotalAmount(),
                new OrderRequestFingerprint(found.getRequestFingerprint())));
    }

    @Override
    public int updateStatusIfCurrent(Long id, Long userId, OrderStatus current, OrderStatus target) {
        return orderMapper.update(null, new LambdaUpdateWrapper<OrderEntity>()
                .eq(OrderEntity::getId, id)
                .eq(OrderEntity::getUserId, userId)
                .eq(OrderEntity::getStatus, current.name())
                .set(OrderEntity::getStatus, target.name())
                .setSql("update_time = CURRENT_TIMESTAMP"));
    }

    @Override
    public int updateStatusIfCurrent(Long id, OrderStatus current, OrderStatus target) {
        return orderMapper.update(null, new LambdaUpdateWrapper<OrderEntity>()
                .eq(OrderEntity::getId, id)
                .eq(OrderEntity::getStatus, current.name())
                .set(OrderEntity::getStatus, target.name())
                .setSql("update_time = CURRENT_TIMESTAMP"));
    }

    @Override
    public Optional<OrderStatus> getStatusByIdAndUserId(Long id, Long userId) {
        OrderEntity entity = orderMapper.selectOne(new LambdaQueryWrapper<OrderEntity>()
                .select(OrderEntity::getStatus)
                .eq(OrderEntity::getId, id)
                .eq(OrderEntity::getUserId, userId));
        return Optional.ofNullable(entity).map(OrderEntity::getStatus).map(OrderStatus::valueOf);
    }

    @Override
    public Optional<OrderStatus> getStatusById(Long id) {
        OrderEntity entity = orderMapper.selectOne(new LambdaQueryWrapper<OrderEntity>()
                .select(OrderEntity::getStatus)
                .eq(OrderEntity::getId, id));
        return Optional.ofNullable(entity).map(OrderEntity::getStatus).map(OrderStatus::valueOf);
    }
}
