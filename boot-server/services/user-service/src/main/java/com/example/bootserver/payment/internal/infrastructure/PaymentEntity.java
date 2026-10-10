package com.example.bootserver.payment.internal.infrastructure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** t_payment 的数据库映射。 */
@Data
@TableName("t_payment")
public class PaymentEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String payNo;
    private BigDecimal amount;
    private String status;
    private LocalDateTime createTime;
}
