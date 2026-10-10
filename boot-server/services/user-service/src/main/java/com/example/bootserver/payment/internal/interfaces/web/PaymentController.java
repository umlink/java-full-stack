package com.example.bootserver.payment.internal.interfaces.web;

import com.example.bootserver.common.result.Result;
import com.example.bootserver.config.OpenApiConfig;
import com.example.bootserver.payment.internal.application.command.PaymentResult;
import com.example.bootserver.payment.internal.application.command.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当前用户订单的同步模拟支付入口。 */
@RestController
@RequestMapping("/orders")
@Tag(name = "支付", description = "当前用户订单的同步模拟支付")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTHENTICATION)
public class PaymentController {
    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "模拟支付订单", description = "仅 CREATED 订单可支付；成功后订单、支付单和库存实扣原子提交。")
    public Result<PaymentResponse> pay(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        PaymentResult payment = payments.pay(userId, id);
        return Result.ok(new PaymentResponse(payment.id(), payment.orderId(), payment.payNo(),
                payment.amount(), payment.status()));
    }
}
