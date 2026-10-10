package com.example.bootserver.cart.internal.infrastructure.redis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.bootserver.cart.internal.domain.CartItem;
import com.example.bootserver.cart.internal.domain.CartItemRepository;
import com.example.bootserver.cart.internal.domain.CartQuantity;
import com.example.bootserver.cart.internal.domain.CartStoreUnavailableException;
import com.example.bootserver.cart.internal.infrastructure.CartItemEntity;
import com.example.bootserver.cart.internal.infrastructure.CartItemMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Redis Hash 购物车适配器；旧数据库行只在首次访问时迁入并清理。 */
@Repository
@ConditionalOnProperty(prefix = "app.cart.redis", name = "enabled", havingValue = "true")
public class RedisCartItemRepository implements CartItemRepository {

    public static final String CART_KEY_PREFIX = "bootmall:cart:";
    public static final String MIGRATION_KEY_PREFIX = "bootmall:cart:migrated:";
    public static final String MIGRATION_LOCK_KEY_PREFIX = "bootmall:cart:migration-lock:";
    public static final String ITEM_SEQUENCE_KEY = "bootmall:cart:item-sequence";
    public static final String OPERATIONS_METRIC = "bootmall.cart.redis.operations";

    private static final String MIGRATION_PENDING = "pending-cleanup";
    private static final String MIGRATION_COMPLETE = "complete";
    private static final Logger log = LoggerFactory.getLogger(RedisCartItemRepository.class);

    private static final DefaultRedisScript<Long> INCREASE_SCRIPT = script("""
            local current = redis.call('HGET', KEYS[1], ARGV[1])
            if not current then return 0 end
            local separator = string.find(current, ':')
            if not separator then return -2 end
            local quantity = tonumber(string.sub(current, separator + 1))
            local delta = tonumber(ARGV[2])
            local maximum = tonumber(ARGV[3])
            if not quantity or quantity + delta > maximum then return -1 end
            redis.call('HSET', KEYS[1], ARGV[1], string.sub(current, 1, separator) .. tostring(quantity + delta))
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            return 1
            """);

    private static final DefaultRedisScript<Long> INSERT_SCRIPT = script("""
            if redis.call('HEXISTS', KEYS[1], ARGV[1]) == 1 then return 0 end
            redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
            redis.call('EXPIRE', KEYS[1], ARGV[3])
            return 1
            """);

    private static final DefaultRedisScript<Long> REPLACE_SCRIPT = script("""
            local entries = redis.call('HGETALL', KEYS[1])
            for index = 1, #entries, 2 do
              local separator = string.find(entries[index + 1], ':')
              if separator and string.sub(entries[index + 1], 1, separator - 1) == ARGV[1] then
                redis.call('HSET', KEYS[1], entries[index], ARGV[1] .. ':' .. ARGV[2])
                redis.call('EXPIRE', KEYS[1], ARGV[3])
                return 1
              end
            end
            return 0
            """);

    private static final DefaultRedisScript<Long> DELETE_SCRIPT = script("""
            local entries = redis.call('HGETALL', KEYS[1])
            for index = 1, #entries, 2 do
              local separator = string.find(entries[index + 1], ':')
              if separator and string.sub(entries[index + 1], 1, separator - 1) == ARGV[1] then
                redis.call('HDEL', KEYS[1], entries[index])
                return 1
              end
            end
            return 0
            """);

    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = script("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """);

    private final StringRedisTemplate redisTemplate;
    private final CartItemMapper mapper;
    private final CartRedisProperties properties;
    private final MeterRegistry meterRegistry;
    private final TransactionTemplate independentTransaction;

    public RedisCartItemRepository(StringRedisTemplate redisTemplate,
                                   CartItemMapper mapper,
                                   CartRedisProperties properties,
                                   MeterRegistry meterRegistry,
                                   PlatformTransactionManager transactionManager) {
        this.redisTemplate = redisTemplate;
        this.mapper = mapper;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.independentTransaction = new TransactionTemplate(transactionManager);
        this.independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public int increase(Long userId, Long skuId, CartQuantity delta) {
        return execute("increase", () -> {
            ensureMigrated(userId);
            Long result = redisTemplate.execute(INCREASE_SCRIPT, List.of(cartKey(userId)), skuId.toString(),
                    Integer.toString(delta.value()), Integer.toString(CartQuantity.MAX), ttlSeconds());
            if (result == null || result == -2) {
                throw new IllegalStateException("购物车条目格式无效");
            }
            if (result == -1) {
                increment("increase", "overflow");
                return 0;
            }
            increment("increase", result == 1 ? "success" : "missing");
            return result.intValue();
        });
    }

    @Override
    public void insert(Long userId, Long skuId, CartQuantity quantity) {
        try {
            ensureMigrated(userId);
            Long itemId = nextItemId();
            Long result = redisTemplate.execute(INSERT_SCRIPT, List.of(cartKey(userId)), skuId.toString(),
                    encode(itemId, quantity.value()), ttlSeconds());
            if (result == null) {
                throw new IllegalStateException("Redis 未返回插入结果");
            }
            if (result == 0) {
                increment("insert", "conflict");
                throw new DuplicateKeyException("同一用户与 SKU 已有购物车条目");
            }
            increment("insert", "success");
        } catch (DuplicateKeyException exception) {
            throw exception;
        } catch (CartStoreUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable("insert", userId, exception);
        }
    }

    @Override
    public int replace(Long userId, Long itemId, CartQuantity quantity) {
        return execute("replace", () -> {
            ensureMigrated(userId);
            Long result = redisTemplate.execute(REPLACE_SCRIPT, List.of(cartKey(userId)), itemId.toString(),
                    Integer.toString(quantity.value()), ttlSeconds());
            if (result == null) {
                throw new IllegalStateException("Redis 未返回改量结果");
            }
            increment("replace", result == 1 ? "success" : "missing");
            return result.intValue();
        });
    }

    @Override
    public int delete(Long userId, Long itemId) {
        return execute("delete", () -> {
            ensureMigrated(userId);
            Long result = redisTemplate.execute(DELETE_SCRIPT, List.of(cartKey(userId)), itemId.toString());
            if (result == null) {
                throw new IllegalStateException("Redis 未返回删除结果");
            }
            increment("delete", result == 1 ? "success" : "missing");
            return result.intValue();
        });
    }

    @Override
    public List<CartItem> listByUserId(Long userId) {
        return execute("list", () -> {
            ensureMigrated(userId);
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(cartKey(userId));
            if (!entries.isEmpty()) {
                redisTemplate.expire(cartKey(userId), properties.getTtl());
            }
            touchMigrationMarker(userId);
            List<CartItem> items = new ArrayList<>(entries.size());
            entries.forEach((skuId, encoded) -> items.add(decode(skuId.toString(), encoded.toString())));
            items.sort(Comparator.comparing(CartItem::id));
            increment("list", "success");
            return List.copyOf(items);
        });
    }

    private void ensureMigrated(Long userId) {
        String marker = redisTemplate.opsForValue().get(migrationKey(userId));
        if (MIGRATION_COMPLETE.equals(marker)) {
            touchMigrationMarker(userId);
            return;
        }
        migrateUnderLock(userId);
    }

    private void migrateUnderLock(Long userId) {
        String lockKey = migrationLockKey(userId);
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, token, properties.getMigrationLockTtl());
        if (!Boolean.TRUE.equals(acquired)) {
            waitForMigration(userId);
            return;
        }
        try {
            String marker = redisTemplate.opsForValue().get(migrationKey(userId));
            if (MIGRATION_COMPLETE.equals(marker)) {
                touchMigrationMarker(userId);
                return;
            }
            if (!MIGRATION_PENDING.equals(marker)) {
                migrateLegacyItems(userId);
                redisTemplate.opsForValue().set(migrationKey(userId), MIGRATION_PENDING, properties.getTtl());
            }
            deleteLegacyItems(userId);
            redisTemplate.opsForValue().set(migrationKey(userId), MIGRATION_COMPLETE, properties.getTtl());
            increment("migration", "success");
        } finally {
            redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(lockKey), token);
        }
    }

    private void migrateLegacyItems(Long userId) {
        Boolean cartExists = redisTemplate.hasKey(cartKey(userId));
        if (Boolean.TRUE.equals(cartExists)) {
            redisTemplate.expire(cartKey(userId), properties.getTtl());
            return;
        }
        List<CartItemEntity> legacyItems = independentTransaction.execute(status -> mapper.selectList(
                new LambdaQueryWrapper<CartItemEntity>()
                        .eq(CartItemEntity::getUserId, userId)
                        .orderByAsc(CartItemEntity::getId)));
        if (legacyItems == null || legacyItems.isEmpty()) {
            return;
        }
        Map<String, String> entries = legacyItems.stream().collect(java.util.stream.Collectors.toMap(
                item -> item.getSkuId().toString(),
                item -> encode(item.getId(), item.getQuantity())));
        redisTemplate.opsForHash().putAll(cartKey(userId), entries);
        redisTemplate.expire(cartKey(userId), properties.getTtl());
    }

    private void deleteLegacyItems(Long userId) {
        independentTransaction.executeWithoutResult(status -> mapper.deleteByUserId(userId));
    }

    private void waitForMigration(Long userId) {
        long deadline = System.nanoTime() + properties.getMigrationWaitTimeout().toNanos();
        while (System.nanoTime() < deadline) {
            if (MIGRATION_COMPLETE.equals(redisTemplate.opsForValue().get(migrationKey(userId)))) {
                touchMigrationMarker(userId);
                increment("migration", "waited");
                return;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new CartStoreUnavailableException("等待购物车迁移时线程被中断", exception);
            }
        }
        increment("migration", "timeout");
        throw new CartStoreUnavailableException("等待购物车迁移超时");
    }

    private Long nextItemId() {
        redisTemplate.opsForValue().setIfAbsent(ITEM_SEQUENCE_KEY,
                Long.toString(System.currentTimeMillis() * 1000));
        Long itemId = redisTemplate.opsForValue().increment(ITEM_SEQUENCE_KEY);
        if (itemId == null) {
            throw new IllegalStateException("Redis 未返回购物车条目 ID");
        }
        return itemId;
    }

    private void touchMigrationMarker(Long userId) {
        redisTemplate.expire(migrationKey(userId), properties.getTtl());
    }

    private CartItem decode(String skuId, String value) {
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalStateException("购物车条目格式无效");
        }
        try {
            long itemId = Long.parseLong(value.substring(0, separator));
            int quantity = Integer.parseInt(value.substring(separator + 1));
            return new CartItem(itemId, Long.parseLong(skuId), new CartQuantity(quantity).value());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("购物车条目格式无效", exception);
        }
    }

    private String encode(Long itemId, int quantity) {
        return itemId + ":" + quantity;
    }

    private String ttlSeconds() {
        return Long.toString(properties.getTtl().toSeconds());
    }

    private String cartKey(Long userId) {
        return CART_KEY_PREFIX + userId;
    }

    private String migrationKey(Long userId) {
        return MIGRATION_KEY_PREFIX + userId;
    }

    private String migrationLockKey(Long userId) {
        return MIGRATION_LOCK_KEY_PREFIX + userId;
    }

    private <T> T execute(String operation, RedisOperation<T> action) {
        try {
            return action.run();
        } catch (CartStoreUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable(operation, null, exception);
        }
    }

    private CartStoreUnavailableException unavailable(String operation, Long userId, RuntimeException cause) {
        increment(operation, "error");
        log.error("Redis 购物车操作失败，operation={}, userId={}", operation, userId, cause);
        return new CartStoreUnavailableException("购物车服务暂时不可用", cause);
    }

    private void increment(String operation, String result) {
        Counter.builder(OPERATIONS_METRIC)
                .tag("operation", operation)
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }

    private static DefaultRedisScript<Long> script(String source) {
        return new DefaultRedisScript<>(source, Long.class);
    }

    @FunctionalInterface
    private interface RedisOperation<T> {
        T run();
    }
}
