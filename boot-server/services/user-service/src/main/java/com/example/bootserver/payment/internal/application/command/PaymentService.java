package com.example.bootserver.payment.internal.application.command;

import com.example.bootserver.inventory.api.InventoryCommandApi;
import com.example.bootserver.order.api.OrderPaymentApi;
import com.example.bootserver.order.api.PayableOrder;
import com.example.bootserver.payment.internal.domain.Payment;
import com.example.bootserver.payment.internal.domain.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 同步模拟支付编排；订单迁移、支付单和库存实扣共享同一本地事务。 */
@Service
public class PaymentService {
    private final OrderPaymentApi orders;
    private final PaymentRepository payments;
    private final PaymentNumberGenerator numbers;
    private final InventoryCommandApi stocks;

    public PaymentService(OrderPaymentApi orders, PaymentRepository payments,
                          PaymentNumberGenerator numbers, InventoryCommandApi stocks) {
        this.orders = orders;
        this.payments = payments;
        this.numbers = numbers;
        this.stocks = stocks;
    }

    @Transactional
    public PaymentResult pay(Long userId, Long orderId) {
        PayableOrder order = orders.claimForPayment(userId, orderId);
        Payment payment = Payment.success(order.id(), numbers.nextPayNo(), order.totalAmount());
        Long paymentId = payments.insert(payment);
        order.items().forEach(item -> stocks.settleReserved(item.skuId(), item.quantity()));
        return new PaymentResult(paymentId, payment.orderId(), payment.payNo(), payment.amount(), payment.status());
    }
}
