package com.example.bootserver.order.internal.domain;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 订单聚合：总额由明细快照计算，不接受客户端传入金额。 */
public record Order(Long userId, String orderNo, IdempotencyKey idempotencyKey,
                    OrderRequestFingerprint fingerprint, OrderStatus status,
                    BigDecimal totalAmount, List<OrderLine> lines) {

    public Order {
        if (userId == null || userId <= 0 || orderNo == null || orderNo.isBlank()
                || idempotencyKey == null || fingerprint == null || status == null
                || totalAmount == null || lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("订单不合法");
        }
        if (lines.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("订单明细不合法");
        }
        lines = List.copyOf(lines);
        Set<Long> skuIds = new HashSet<>();
        for (OrderLine line : lines) {
            if (line == null || !skuIds.add(line.skuId())) {
                throw new IllegalArgumentException("订单不能包含重复 SKU");
            }
        }
        if (!fingerprint.equals(OrderRequestFingerprint.from(userId, lines.stream()
                .map(line -> new OrderSelection(line.skuId(), line.quantity())).toList()))) {
            throw new IllegalArgumentException("请求指纹与订单明细不一致");
        }
        BigDecimal calculated = lines.stream().map(OrderLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (calculated.setScale(2).precision() > 19 || calculated.scale() > 2
                || calculated.compareTo(totalAmount) != 0) {
            throw new IllegalArgumentException("订单金额超出范围或与明细不一致");
        }
    }

    public static Order create(Long userId, String orderNo, IdempotencyKey key,
                               OrderRequestFingerprint fingerprint, List<OrderLine> lines) {
        if (lines == null || lines.isEmpty() || lines.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("订单明细不合法");
        }
        BigDecimal total = lines.stream().map(OrderLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Order(userId, orderNo, key, fingerprint, OrderStatus.CREATED, total, lines);
    }
}
