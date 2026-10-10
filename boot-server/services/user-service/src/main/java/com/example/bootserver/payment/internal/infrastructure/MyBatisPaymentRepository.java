package com.example.bootserver.payment.internal.infrastructure;

import com.example.bootserver.payment.internal.domain.Payment;
import com.example.bootserver.payment.internal.domain.PaymentRepository;
import org.springframework.stereotype.Repository;

/** 把成功支付领域对象转换为持久化对象。 */
@Repository
public class MyBatisPaymentRepository implements PaymentRepository {
    private final PaymentMapper mapper;

    public MyBatisPaymentRepository(PaymentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Long insert(Payment payment) {
        PaymentEntity entity = new PaymentEntity();
        entity.setOrderId(payment.orderId());
        entity.setPayNo(payment.payNo());
        entity.setAmount(payment.amount());
        entity.setStatus(payment.status().name());
        mapper.insert(entity);
        return entity.getId();
    }
}
