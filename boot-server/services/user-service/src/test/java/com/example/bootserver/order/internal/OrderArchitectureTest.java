package com.example.bootserver.order.internal;

import com.example.bootserver.order.internal.application.command.OrderService;
import com.example.bootserver.order.internal.application.command.OrderCancellationService;
import com.example.bootserver.order.internal.application.command.OrderCommandSnapshotRepository;
import com.example.bootserver.order.internal.application.command.OrderFulfillmentService;
import com.example.bootserver.order.internal.application.command.OrderPaymentService;
import com.example.bootserver.order.internal.application.query.OrderQueryRepository;
import com.example.bootserver.order.internal.application.query.OrderQueryService;
import com.example.bootserver.order.internal.domain.Order;
import com.example.bootserver.order.internal.domain.OrderLine;
import com.example.bootserver.order.internal.domain.OrderRepository;
import com.example.bootserver.order.internal.domain.OrderStatus;
import com.example.bootserver.order.internal.domain.IdempotencyKey;
import com.example.bootserver.order.internal.domain.OrderSelection;
import com.example.bootserver.order.internal.domain.OrderRequestFingerprint;
import com.example.bootserver.order.internal.domain.ExistingOrder;
import com.example.bootserver.order.internal.domain.IllegalOrderStateException;
import com.example.bootserver.order.internal.infrastructure.OrderEntity;
import com.example.bootserver.order.internal.infrastructure.OrderItemEntity;
import com.example.bootserver.order.internal.infrastructure.OrderItemMapper;
import com.example.bootserver.order.internal.infrastructure.OrderMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 订单领域不依赖 Web、Spring 或 ORM；应用层使用聚合仓储端口。 */
class OrderArchitectureTest {
    @Test
    void orderDomainIsFrameworkFree() {
        for (Class<?> type : List.of(Order.class, OrderLine.class, OrderStatus.class, OrderRepository.class,
                IdempotencyKey.class, OrderSelection.class, OrderRequestFingerprint.class, ExistingOrder.class,
                IllegalOrderStateException.class)) {
            assertThat(type.getAnnotations()).isEmpty();
            assertThat(Arrays.stream(type.getDeclaredFields()).map(Field::getType))
                    .allMatch(fieldType -> fieldType.isPrimitive()
                            || fieldType.getPackageName().startsWith("java.")
                            || fieldType.getPackageName().equals("com.example.bootserver.order.internal.domain"));
        }
    }

    @Test
    void orderApplicationDoesNotDependOnPersistenceEntitiesOrMappers() {
        assertThat(Arrays.stream(OrderService.class.getDeclaredFields()).map(Field::getType))
                .contains(OrderRepository.class)
                .doesNotContain(OrderMapper.class, OrderItemMapper.class,
                        OrderEntity.class, OrderItemEntity.class);
        assertThat(Arrays.stream(OrderQueryService.class.getDeclaredFields()).map(Field::getType))
                .containsExactly(OrderQueryRepository.class)
                .doesNotContain(OrderMapper.class, OrderItemMapper.class,
                        OrderEntity.class, OrderItemEntity.class);
        assertThat(Arrays.stream(OrderPaymentService.class.getDeclaredFields()).map(Field::getType))
                .contains(OrderRepository.class, OrderCommandSnapshotRepository.class)
                .doesNotContain(OrderMapper.class, OrderItemMapper.class,
                        OrderEntity.class, OrderItemEntity.class);
        assertThat(Arrays.stream(OrderCancellationService.class.getDeclaredFields()).map(Field::getType))
                .contains(OrderRepository.class, OrderCommandSnapshotRepository.class)
                .doesNotContain(OrderMapper.class, OrderItemMapper.class,
                        OrderEntity.class, OrderItemEntity.class);
        assertThat(Arrays.stream(OrderFulfillmentService.class.getDeclaredFields()).map(Field::getType))
                .containsExactly(OrderRepository.class)
                .doesNotContain(OrderMapper.class, OrderItemMapper.class,
                        OrderEntity.class, OrderItemEntity.class);
    }
}
