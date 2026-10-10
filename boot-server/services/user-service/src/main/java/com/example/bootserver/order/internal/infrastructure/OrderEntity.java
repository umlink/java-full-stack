package com.example.bootserver.order.internal.infrastructure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** t_order 的数据库映射，不承担领域规则。 */
@Data
@TableName("t_order")
public class OrderEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private String idempotencyKey;
    private String requestFingerprint;
    private String status;
    private BigDecimal totalAmount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
