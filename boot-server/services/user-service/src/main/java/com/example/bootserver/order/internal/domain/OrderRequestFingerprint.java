package com.example.bootserver.order.internal.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** 对用户和规范化 SKU 数量列表计算 SHA-256，避免 JSON 字段及数组顺序影响重放。 */
public record OrderRequestFingerprint(String value) {
    public OrderRequestFingerprint {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("请求指纹不合法");
        }
    }

    public static OrderRequestFingerprint from(Long userId, List<OrderSelection> selections) {
        if (userId == null || userId <= 0 || selections == null || selections.isEmpty()) {
            throw new IllegalArgumentException("下单语义不合法");
        }
        if (selections.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("订单明细不合法");
        }
        StringBuilder canonical = new StringBuilder("v1\n").append(userId).append('\n');
        Long previousSkuId = null;
        for (OrderSelection selection : selections.stream()
                .sorted(Comparator.comparing(OrderSelection::skuId)).toList()) {
            if (previousSkuId != null && previousSkuId.equals(selection.skuId())) {
                throw new IllegalArgumentException("订单不能包含重复 SKU");
            }
            canonical.append(selection.skuId()).append(':').append(selection.quantity()).append('\n');
            previousSkuId = selection.skuId();
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return new OrderRequestFingerprint(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 不支持 SHA-256", exception);
        }
    }
}
