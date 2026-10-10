package com.example.bootserver.payment;

import com.example.bootserver.inventory.api.InventoryCommandApi;
import com.example.bootserver.order.api.OrderPaymentApi;
import com.example.bootserver.payment.internal.application.command.PaymentNumberGenerator;
import com.example.bootserver.payment.internal.application.command.PaymentService;
import com.example.bootserver.payment.internal.domain.Payment;
import com.example.bootserver.payment.internal.domain.PaymentRepository;
import com.example.bootserver.payment.internal.domain.PaymentStatus;
import com.example.bootserver.payment.internal.infrastructure.PaymentEntity;
import com.example.bootserver.payment.internal.infrastructure.PaymentMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 支付领域保持纯 Java，跨上下文写入只通过显式应用契约。 */
class PaymentArchitectureTest {
    @Test
    void paymentDomainIsFrameworkFree() {
        for (Class<?> type : List.of(Payment.class, PaymentStatus.class, PaymentRepository.class)) {
            assertThat(type.getAnnotations()).isEmpty();
            assertThat(Arrays.stream(type.getDeclaredFields()).map(Field::getType))
                    .allMatch(fieldType -> fieldType.isPrimitive()
                            || fieldType.getPackageName().startsWith("java.")
                            || fieldType.getPackageName().equals("com.example.bootserver.payment.internal.domain"));
        }
    }

    @Test
    void paymentApplicationUsesPortsAndContextServices() {
        assertThat(Arrays.stream(PaymentService.class.getDeclaredFields()).map(Field::getType))
                .containsExactly(OrderPaymentApi.class, PaymentRepository.class,
                        PaymentNumberGenerator.class, InventoryCommandApi.class)
                .doesNotContain(PaymentMapper.class, PaymentEntity.class);
    }
}
