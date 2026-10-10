package com.example.bootserver.inventory.internal.infrastructure.redis;

import com.example.bootserver.inventory.api.InventoryReservationId;
import com.example.bootserver.inventory.api.InventoryReservationResult;
import com.example.bootserver.inventory.internal.application.command.InventoryReconciliationResult;
import com.example.bootserver.inventory.internal.application.command.InventoryReconciliationStatus;
import com.example.bootserver.inventory.internal.application.command.InventoryReservationStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/** Lua 库存预占副本；异常时降级数据库，数据库条件更新始终执行。 */
@Component
@ConditionalOnProperty(prefix = "app.inventory.redis", name = "enabled", havingValue = "true")
public class RedisInventoryReservationStore implements InventoryReservationStore {

    public static final String STOCK_KEY_PREFIX = "bootmall:inventory:stock:";
    public static final String INITIALIZED_FIELD = "_initialized";
    public static final String AVAILABLE_FIELD = "_available";
    public static final String RESERVATION_FIELD_PREFIX = "reservation:";
    public static final String RESERVATIONS_METRIC = "bootmall.inventory.redis.reservations";

    /** Redis 3 Lua 使用 IEEE-754 double，超出该范围时直接走数据库，避免整数精度丢失。 */
    public static final long LUA_SAFE_INTEGER_MAX = 9_007_199_254_740_991L;

    private static final Logger log = LoggerFactory.getLogger(RedisInventoryReservationStore.class);

    private static final DefaultRedisScript<Long> PRE_RESERVE_SCRIPT = script("""
            local initialized = redis.call('HGET', KEYS[1], ARGV[1])
            if not initialized then
              redis.call('HSET', KEYS[1], ARGV[1], '1')
              redis.call('HSET', KEYS[1], ARGV[2], ARGV[4])
            end
            local current = redis.call('HGET', KEYS[1], ARGV[3])
            local requested = tonumber(ARGV[5])
            if current then
              local quantity, state, attempts = string.match(current, '^(%d+)|([A-Z]+)|(%d+)$')
              quantity = tonumber(quantity)
              attempts = tonumber(attempts)
              if not quantity or not attempts then return -2 end
              if quantity ~= requested then return -1 end
              if state == 'PENDING' then
                redis.call('HSET', KEYS[1], ARGV[3], ARGV[5] .. '|PENDING|' .. tostring(attempts + 1))
                redis.call('EXPIRE', KEYS[1], ARGV[6])
                return 2
              end
              if state == 'RESERVED' or state == 'SETTLED' then
                redis.call('EXPIRE', KEYS[1], ARGV[6])
                return 3
              end
              if state ~= 'RELEASED' then return -2 end
            end
            local available = tonumber(redis.call('HGET', KEYS[1], ARGV[2]))
            if not available then return -2 end
            if available < requested then
              redis.call('EXPIRE', KEYS[1], ARGV[6])
              return 0
            end
            redis.call('HINCRBY', KEYS[1], ARGV[2], -requested)
            redis.call('HSET', KEYS[1], ARGV[3], ARGV[5] .. '|PENDING|1')
            redis.call('EXPIRE', KEYS[1], ARGV[6])
            return 1
            """);

    private static final DefaultRedisScript<Long> COMPLETE_SCRIPT = script("""
            local current = redis.call('HGET', KEYS[1], ARGV[1])
            if not current then return 0 end
            local quantity, state, attempts = string.match(current, '^(%d+)|([A-Z]+)|(%d+)$')
            if not quantity or tonumber(quantity) ~= tonumber(ARGV[2]) then return -1 end
            if state == 'PENDING' then
              redis.call('HSET', KEYS[1], ARGV[1], quantity .. '|RESERVED|0')
              redis.call('EXPIRE', KEYS[1], ARGV[3])
              return 1
            end
            if state == 'RESERVED' or state == 'SETTLED' then
              redis.call('EXPIRE', KEYS[1], ARGV[3])
              return 2
            end
            return -2
            """);

    private static final DefaultRedisScript<Long> COMPENSATE_SCRIPT = script("""
            local current = redis.call('HGET', KEYS[1], ARGV[1])
            if not current then return 0 end
            local quantity, state, attempts = string.match(current, '^(%d+)|([A-Z]+)|(%d+)$')
            quantity = tonumber(quantity)
            attempts = tonumber(attempts)
            if not quantity or not attempts or quantity ~= tonumber(ARGV[2]) then return -1 end
            if state ~= 'PENDING' then return 0 end
            if attempts > 1 then
              redis.call('HSET', KEYS[1], ARGV[1], tostring(quantity) .. '|PENDING|' .. tostring(attempts - 1))
              redis.call('EXPIRE', KEYS[1], ARGV[3])
              return 1
            end
            redis.call('HINCRBY', KEYS[1], ARGV[4], quantity)
            redis.call('HSET', KEYS[1], ARGV[1], tostring(quantity) .. '|RELEASED|0')
            redis.call('EXPIRE', KEYS[1], ARGV[3])
            return 2
            """);

    private static final DefaultRedisScript<Long> SETTLE_SCRIPT = script("""
            local current = redis.call('HGET', KEYS[1], ARGV[1])
            if not current then return 0 end
            local quantity, state, attempts = string.match(current, '^(%d+)|([A-Z]+)|(%d+)$')
            if not quantity or tonumber(quantity) ~= tonumber(ARGV[2]) then return -1 end
            if state == 'PENDING' or state == 'RESERVED' then
              redis.call('HSET', KEYS[1], ARGV[1], quantity .. '|SETTLED|0')
              redis.call('EXPIRE', KEYS[1], ARGV[3])
              return 1
            end
            if state == 'SETTLED' then return 2 end
            return -2
            """);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = script("""
            local current = redis.call('HGET', KEYS[1], ARGV[1])
            if not current then return 0 end
            local quantity, state, attempts = string.match(current, '^(%d+)|([A-Z]+)|(%d+)$')
            quantity = tonumber(quantity)
            if not quantity or quantity ~= tonumber(ARGV[2]) then return -1 end
            if state == 'PENDING' or state == 'RESERVED' then
              redis.call('HINCRBY', KEYS[1], ARGV[4], quantity)
              redis.call('HSET', KEYS[1], ARGV[1], tostring(quantity) .. '|RELEASED|0')
              redis.call('EXPIRE', KEYS[1], ARGV[3])
              return 1
            end
            if state == 'RELEASED' then return 2 end
            return -2
            """);

    private static final DefaultRedisScript<String> RECONCILE_SCRIPT = stringScript("""
            local current = redis.call('HGET', KEYS[1], ARGV[1])
            if not current then
              if redis.call('EXISTS', KEYS[1]) == 1 then
                redis.call('DEL', KEYS[1])
                return 'REPAIRED|<missing>'
              end
              return 'MISSING'
            end
            if current == ARGV[2] then
              return 'CONSISTENT|' .. current
            end
            redis.call('DEL', KEYS[1])
            return 'REPAIRED|' .. current
            """);

    private final StringRedisTemplate redisTemplate;
    private final InventoryRedisProperties properties;
    private final MeterRegistry meterRegistry;

    public RedisInventoryReservationStore(StringRedisTemplate redisTemplate,
                                          InventoryRedisProperties properties,
                                          MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public InventoryReservationResult preReserve(InventoryReservationId reservationId, Long skuId,
                                                 int quantity, long initialAvailable) {
        if (initialAvailable > LUA_SAFE_INTEGER_MAX) {
            increment("pre_reserve", "unsafe_range");
            return InventoryReservationResult.BYPASSED;
        }
        try {
            Long result = redisTemplate.execute(PRE_RESERVE_SCRIPT, List.of(key(skuId)),
                    INITIALIZED_FIELD, AVAILABLE_FIELD, reservationField(reservationId),
                    Long.toString(initialAvailable), Integer.toString(quantity), ttlSeconds());
            InventoryReservationResult outcome = switch ((int) requiredResult(result)) {
                case 0 -> InventoryReservationResult.INSUFFICIENT;
                case 1 -> InventoryReservationResult.ACQUIRED;
                case 2 -> InventoryReservationResult.JOINED;
                case 3 -> InventoryReservationResult.ALREADY_RESERVED;
                case -1 -> InventoryReservationResult.CONFLICT;
                default -> throw new IllegalStateException("Redis 库存预占状态损坏");
            };
            increment("pre_reserve", outcome.name().toLowerCase(java.util.Locale.ROOT));
            return outcome;
        } catch (RuntimeException exception) {
            increment("pre_reserve", "error");
            log.warn("Redis 库存预占失败，降级数据库条件更新，skuId={}", skuId, exception);
            return InventoryReservationResult.BYPASSED;
        }
    }

    @Override
    public void complete(InventoryReservationId reservationId, Long skuId, int quantity) {
        executeStateChange("complete", COMPLETE_SCRIPT, reservationId, skuId, quantity, false);
    }

    @Override
    public void compensate(InventoryReservationId reservationId, Long skuId, int quantity) {
        executeStateChange("compensate", COMPENSATE_SCRIPT, reservationId, skuId, quantity, true);
    }

    @Override
    public void settle(InventoryReservationId reservationId, Long skuId, int quantity) {
        executeStateChange("settle", SETTLE_SCRIPT, reservationId, skuId, quantity, false);
    }

    @Override
    public void release(InventoryReservationId reservationId, Long skuId, int quantity) {
        executeStateChange("release", RELEASE_SCRIPT, reservationId, skuId, quantity, true);
    }

    @Override
    public void invalidate(Long skuId) {
        try {
            Boolean deleted = redisTemplate.delete(key(skuId));
            increment("invalidate", Boolean.TRUE.equals(deleted) ? "deleted" : "missing");
        } catch (RuntimeException exception) {
            increment("invalidate", "error");
            log.warn("Redis 库存副本失效失败，等待 TTL 或对账收敛，skuId={}", skuId, exception);
        }
    }

    @Override
    public InventoryReconciliationResult reconcile(Long skuId, long databaseAvailable) {
        String value = redisTemplate.execute(RECONCILE_SCRIPT, List.of(key(skuId)),
                AVAILABLE_FIELD, Long.toString(databaseAvailable));
        if (value == null) {
            throw new IllegalStateException("Redis 未返回库存对账结果");
        }
        if (value.equals("MISSING")) {
            return InventoryReconciliationResult.missing();
        }
        int separator = value.indexOf('|');
        if (separator <= 0) {
            throw new IllegalStateException("Redis 库存对账结果损坏");
        }
        InventoryReconciliationStatus status = InventoryReconciliationStatus.valueOf(
                value.substring(0, separator));
        return new InventoryReconciliationResult(status, value.substring(separator + 1));
    }

    private void executeStateChange(String operation, DefaultRedisScript<Long> script,
                                    InventoryReservationId reservationId, Long skuId,
                                    int quantity, boolean requiresAvailableField) {
        try {
            Long result;
            if (requiresAvailableField) {
                result = redisTemplate.execute(script, List.of(key(skuId)), reservationField(reservationId),
                        Integer.toString(quantity), ttlSeconds(), AVAILABLE_FIELD);
            } else {
                result = redisTemplate.execute(script, List.of(key(skuId)), reservationField(reservationId),
                        Integer.toString(quantity), ttlSeconds());
            }
            long code = requiredResult(result);
            increment(operation, code >= 0 ? resultName(code) : "divergence");
            if (code < 0) {
                log.warn("Redis 库存状态与数据库命令不一致，operation={}, skuId={}, code={}",
                        operation, skuId, code);
            }
        } catch (RuntimeException exception) {
            increment(operation, "error");
            log.warn("Redis 库存状态同步失败，等待对账收敛，operation={}, skuId={}",
                    operation, skuId, exception);
        }
    }

    private long requiredResult(Long result) {
        if (result == null) {
            throw new IllegalStateException("Redis 未返回库存脚本结果");
        }
        return result;
    }

    private String resultName(long code) {
        return switch ((int) code) {
            case 0 -> "missing";
            case 1 -> "changed";
            case 2 -> "idempotent";
            default -> "unknown";
        };
    }

    private String key(Long skuId) {
        return STOCK_KEY_PREFIX + "{" + skuId + "}";
    }

    private String reservationField(InventoryReservationId reservationId) {
        return RESERVATION_FIELD_PREFIX + reservationId.value();
    }

    private String ttlSeconds() {
        return Long.toString(properties.getTtl().toSeconds());
    }

    private void increment(String operation, String result) {
        Counter.builder(RESERVATIONS_METRIC)
                .tag("operation", operation)
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }

    private static DefaultRedisScript<Long> script(String source) {
        return new DefaultRedisScript<>(source, Long.class);
    }

    private static DefaultRedisScript<String> stringScript(String source) {
        return new DefaultRedisScript<>(source, String.class);
    }
}
