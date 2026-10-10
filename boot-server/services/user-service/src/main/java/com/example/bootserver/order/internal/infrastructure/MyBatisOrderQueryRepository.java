package com.example.bootserver.order.internal.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.bootserver.order.internal.application.query.OrderDetail;
import com.example.bootserver.order.internal.application.query.AdminOrderPage;
import com.example.bootserver.order.internal.application.query.AdminOrderSummary;
import com.example.bootserver.order.internal.application.query.OrderItemView;
import com.example.bootserver.order.internal.application.query.OrderPage;
import com.example.bootserver.order.internal.application.query.OrderQueryRepository;
import com.example.bootserver.order.internal.application.query.OrderSummary;
import com.example.bootserver.order.internal.application.command.OrderCommandSnapshot;
import com.example.bootserver.order.internal.application.command.OrderCommandSnapshotRepository;
import com.example.bootserver.order.internal.domain.OrderStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 使用订单头分页，并在详情查询中一次批量加载全部明细。 */
@Repository
public class MyBatisOrderQueryRepository implements OrderQueryRepository, OrderCommandSnapshotRepository {
    private final OrderMapper orderMapper;
    private final OrderItemMapper itemMapper;

    public MyBatisOrderQueryRepository(OrderMapper orderMapper, OrderItemMapper itemMapper) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
    }

    @Override
    public OrderPage listByUserId(Long userId, long page, long size) {
        Page<OrderEntity> result = orderMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<OrderEntity>()
                        .eq(OrderEntity::getUserId, userId)
                        .orderByDesc(OrderEntity::getCreateTime)
                        .orderByDesc(OrderEntity::getId));
        List<OrderSummary> items = result.getRecords().stream()
                .map(order -> new OrderSummary(order.getId(), order.getOrderNo(),
                        OrderStatus.valueOf(order.getStatus()), order.getTotalAmount(), order.getCreateTime()))
                .toList();
        return new OrderPage(result.getCurrent(), result.getSize(), result.getTotal(), items);
    }

    @Override
    public Optional<OrderDetail> getByIdAndUserId(Long id, Long userId) {
        OrderEntity order = orderMapper.selectOne(new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getId, id)
                .eq(OrderEntity::getUserId, userId));
        if (order == null) {
            return Optional.empty();
        }
        List<OrderItemView> items = itemMapper.selectList(new LambdaQueryWrapper<OrderItemEntity>()
                        .eq(OrderItemEntity::getOrderId, id)
                        .orderByAsc(OrderItemEntity::getId))
                .stream()
                .map(item -> new OrderItemView(item.getSkuId(), item.getQuantity(), item.getPrice()))
                .toList();
        return Optional.of(new OrderDetail(order.getId(), order.getOrderNo(),
                OrderStatus.valueOf(order.getStatus()), order.getTotalAmount(), order.getCreateTime(), items));
    }

    @Override
    public Optional<OrderCommandSnapshot> getCommandSnapshotByIdAndUserId(Long id, Long userId) {
        OrderEntity order = orderMapper.selectOne(new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getId, id)
                .eq(OrderEntity::getUserId, userId));
        if (order == null) {
            return Optional.empty();
        }
        List<OrderCommandSnapshot.Item> items = itemMapper.selectList(new LambdaQueryWrapper<OrderItemEntity>()
                        .eq(OrderItemEntity::getOrderId, id)
                        .orderByAsc(OrderItemEntity::getId))
                .stream()
                .map(item -> new OrderCommandSnapshot.Item(item.getSkuId(), item.getQuantity()))
                .toList();
        return Optional.of(new OrderCommandSnapshot(order.getId(), order.getTotalAmount(), items));
    }

    @Override
    public AdminOrderPage listAll(long page, long size) {
        Page<OrderEntity> result = orderMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<OrderEntity>()
                        .orderByDesc(OrderEntity::getCreateTime)
                        .orderByDesc(OrderEntity::getId));
        List<AdminOrderSummary> items = result.getRecords().stream()
                .map(order -> new AdminOrderSummary(order.getId(), order.getOrderNo(), order.getUserId(),
                        OrderStatus.valueOf(order.getStatus()), order.getTotalAmount(), order.getCreateTime()))
                .toList();
        return new AdminOrderPage(result.getCurrent(), result.getSize(), result.getTotal(), items);
    }
}
