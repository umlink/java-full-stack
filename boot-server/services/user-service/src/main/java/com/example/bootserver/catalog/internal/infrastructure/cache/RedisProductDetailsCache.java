package com.example.bootserver.catalog.internal.infrastructure.cache;

import com.example.bootserver.catalog.internal.application.command.CatalogCacheInvalidator;
import com.example.bootserver.catalog.internal.application.query.ProductDetails;
import com.example.bootserver.catalog.internal.application.query.ProductDetailsCache;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

/** Redis 商品详情旁路缓存；任何基础设施失败都回退数据库。 */
@Component
@ConditionalOnProperty(prefix = "app.catalog-cache", name = "enabled", havingValue = "true")
public class RedisProductDetailsCache implements ProductDetailsCache, CatalogCacheInvalidator {

    public static final String PRODUCT_DETAILS_KEY_PREFIX = "bootmall:catalog:product-detail:";
    public static final String REQUESTS_METRIC = "bootmall.catalog.cache.requests";
    public static final String WRITES_METRIC = "bootmall.catalog.cache.writes";
    public static final String INVALIDATIONS_METRIC = "bootmall.catalog.cache.invalidations";

    private static final Logger log = LoggerFactory.getLogger(RedisProductDetailsCache.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CatalogCacheProperties properties;
    private final MeterRegistry meterRegistry;

    public RedisProductDetailsCache(StringRedisTemplate redisTemplate,
                                    ObjectMapper objectMapper,
                                    CatalogCacheProperties properties,
                                    MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Optional<ProductDetails> get(Long productId) {
        String key = key(productId);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                increment(REQUESTS_METRIC, "result", "miss");
                return Optional.empty();
            }
            ProductDetails details = objectMapper.readValue(json, ProductDetails.class);
            increment(REQUESTS_METRIC, "result", "hit");
            return Optional.of(details);
        } catch (Exception exception) {
            increment(REQUESTS_METRIC, "result", "error");
            log.warn("读取商品详情缓存失败，回退数据库，productId={}", productId, exception);
            return Optional.empty();
        }
    }

    @Override
    public void put(ProductDetails productDetails) {
        try {
            String json = objectMapper.writeValueAsString(productDetails);
            redisTemplate.opsForValue().set(
                    key(productDetails.id()), json, properties.getProductDetailTtl());
            increment(WRITES_METRIC, "result", "success");
        } catch (Exception exception) {
            increment(WRITES_METRIC, "result", "error");
            log.warn("写入商品详情缓存失败，继续返回数据库结果，productId={}", productDetails.id(), exception);
        }
    }

    @Override
    public void invalidateProduct(Long productId) {
        Runnable invalidation = () -> delete(productId);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidation.run();
                }
            });
            return;
        }
        invalidation.run();
    }

    private void delete(Long productId) {
        try {
            Boolean deleted = redisTemplate.delete(key(productId));
            increment(INVALIDATIONS_METRIC, "result", Boolean.TRUE.equals(deleted) ? "deleted" : "missing");
        } catch (Exception exception) {
            increment(INVALIDATIONS_METRIC, "result", "error");
            log.warn("删除商品详情缓存失败，等待 TTL 收敛，productId={}", productId, exception);
        }
    }

    private void increment(String metricName, String tagName, String tagValue) {
        Counter.builder(metricName).tag(tagName, tagValue).register(meterRegistry).increment();
    }

    private String key(Long productId) {
        return PRODUCT_DETAILS_KEY_PREFIX + productId;
    }
}
