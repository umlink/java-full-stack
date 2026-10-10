package com.example.bootserver.catalog.internal.infrastructure.cache;

import com.example.bootserver.catalog.internal.application.query.CatalogQueryRepository;
import com.example.bootserver.catalog.internal.application.query.ProductDetails;
import com.example.bootserver.catalog.internal.application.query.ProductQueryService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisProductDetailsCacheFailureTest {

    @Test
    void redisReadFailureFallsBackToDatabaseAndIncrementsErrorMetric() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisProductDetailsCache.PRODUCT_DETAILS_KEY_PREFIX + "3"))
                .thenThrow(new RedisConnectionFailureException("test connection failure"));

        ObjectMapper objectMapper = mock(ObjectMapper.class);
        CatalogCacheProperties properties = new CatalogCacheProperties();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RedisProductDetailsCache cache = new RedisProductDetailsCache(
                redisTemplate, objectMapper, properties, meterRegistry);
        CatalogQueryRepository repository = mock(CatalogQueryRepository.class);
        ProductDetails databaseResult = new ProductDetails(3L, "Database product", null, List.of());
        when(repository.findOnSaleProduct(3L)).thenReturn(databaseResult);
        ProductQueryService service = new ProductQueryService(repository, cache);

        assertThat(service.getProductById(3L)).isEqualTo(databaseResult);
        assertThat(meterRegistry.get(RedisProductDetailsCache.REQUESTS_METRIC)
                .tag("result", "error").counter().count()).isEqualTo(1.0);
    }

    @Test
    void invalidationWaitsUntilTheDatabaseTransactionCommits() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        RedisProductDetailsCache cache = new RedisProductDetailsCache(
                redisTemplate, objectMapper, new CatalogCacheProperties(), new SimpleMeterRegistry());
        String key = RedisProductDetailsCache.PRODUCT_DETAILS_KEY_PREFIX + "8";
        when(redisTemplate.delete(key)).thenReturn(true);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            cache.invalidateProduct(8L);
            verify(redisTemplate, never()).delete(key);

            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(redisTemplate).delete(key);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
