package com.example.bootserver.inventory.internal;

import com.example.bootserver.catalog.api.CatalogQueryApi;
import com.example.bootserver.inventory.internal.application.command.StockService;
import com.example.bootserver.inventory.internal.application.command.StockWriteStepService;
import com.example.bootserver.inventory.internal.domain.Stock;
import com.example.bootserver.inventory.internal.domain.StockBelowLockedException;
import com.example.bootserver.inventory.internal.domain.StockRepository;
import com.example.bootserver.inventory.internal.infrastructure.StockEntity;
import com.example.bootserver.inventory.internal.infrastructure.StockMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 重构防线：库存领域 API 保持纯 Java，应用层通过仓储和商品查询契约协作。 */
class StockArchitectureTest {

    @Test
    void domainTypesDoNotExposeFrameworkOrInfrastructureTypes() {
        for (Class<?> type : List.of(Stock.class, StockBelowLockedException.class, StockRepository.class)) {
            assertThat(type.getAnnotations()).isEmpty();
            assertThat(Arrays.stream(type.getDeclaredFields()).map(Field::getType))
                    .allMatch(this::isDomainOrJdkType);
            assertThat(Arrays.stream(type.getDeclaredMethods()).map(Method::getReturnType))
                    .allMatch(this::isDomainOrJdkType);
            assertThat(Arrays.stream(type.getDeclaredMethods())
                    .flatMap(method -> Arrays.stream(method.getParameterTypes())))
                    .allMatch(this::isDomainOrJdkType);
        }
    }

    @Test
    void applicationDependsOnPortsRatherThanMappersOrEntities() {
        assertThat(Arrays.stream(StockService.class.getDeclaredFields()).map(Field::getType))
                .contains(StockRepository.class, StockWriteStepService.class, CatalogQueryApi.class)
                .doesNotContain(StockMapper.class, StockEntity.class);
        assertThat(Arrays.stream(StockWriteStepService.class.getDeclaredFields()).map(Field::getType))
                .contains(StockRepository.class)
                .doesNotContain(StockMapper.class, StockEntity.class);
    }

    private boolean isDomainOrJdkType(Class<?> type) {
        String packageName = type.getPackageName();
        return type.isPrimitive() || packageName.startsWith("java.")
                || packageName.equals("com.example.bootserver.inventory.internal.domain");
    }
}
