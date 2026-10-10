package com.example.bootserver.inventory.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 跨重试稳定的库存预占标识；哈希隐藏上游幂等键中的 Redis 特殊字符。 */
public record InventoryReservationId(String value) {

    public InventoryReservationId {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("库存预占标识不合法");
        }
    }

    public static InventoryReservationId from(String namespace, String stableKey) {
        if (namespace == null || namespace.isBlank() || stableKey == null || stableKey.isBlank()) {
            throw new IllegalArgumentException("库存预占来源不合法");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("v1\n" + namespace + "\n" + stableKey).getBytes(StandardCharsets.UTF_8));
            return new InventoryReservationId(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 不支持 SHA-256", exception);
        }
    }
}
