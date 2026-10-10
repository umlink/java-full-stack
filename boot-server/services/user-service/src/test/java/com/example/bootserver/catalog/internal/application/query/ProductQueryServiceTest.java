package com.example.bootserver.catalog.internal.application.query;

import com.example.bootserver.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductQueryServiceTest {

    @Test
    void repeatedProductDetailsQueryUsesCacheAfterFirstDatabaseRead() {
        CatalogQueryRepository repository = mock(CatalogQueryRepository.class);
        InMemoryProductDetailsCache cache = new InMemoryProductDetailsCache();
        ProductDetails details = details(7L);
        when(repository.findOnSaleProduct(7L)).thenReturn(details);
        ProductQueryService service = new ProductQueryService(repository, cache);

        assertThat(service.getProductById(7L)).isEqualTo(details);
        assertThat(service.getProductById(7L)).isEqualTo(details);

        verify(repository, times(1)).findOnSaleProduct(7L);
        assertThat(cache.get(7L)).contains(details);
    }

    @Test
    void missingProductIsNotCached() {
        CatalogQueryRepository repository = mock(CatalogQueryRepository.class);
        ProductDetailsCache cache = mock(ProductDetailsCache.class);
        when(cache.get(404L)).thenReturn(Optional.empty());
        when(repository.findOnSaleProduct(404L)).thenReturn(null);
        ProductQueryService service = new ProductQueryService(repository, cache);

        assertThatThrownBy(() -> service.getProductById(404L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getProductById(404L)).isInstanceOf(BusinessException.class);

        verify(repository, times(2)).findOnSaleProduct(404L);
        verify(cache, never()).put(org.mockito.ArgumentMatchers.any());
    }

    private ProductDetails details(Long id) {
        return new ProductDetails(id, "Cached product", "details",
                java.util.List.of(new ProductDetails.SkuDetails(9L, "CACHE-SKU", new BigDecimal("19.90"))));
    }

    private static final class InMemoryProductDetailsCache implements ProductDetailsCache {

        private final Map<Long, ProductDetails> values = new HashMap<>();

        @Override
        public Optional<ProductDetails> get(Long productId) {
            return Optional.ofNullable(values.get(productId));
        }

        @Override
        public void put(ProductDetails productDetails) {
            values.put(productDetails.id(), productDetails);
        }
    }
}
