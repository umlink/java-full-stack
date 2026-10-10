package com.example.bootserver.order.internal.infrastructure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** t_order_timeout_outbox 持久化映射。 */
@Data
@TableName("t_order_timeout_outbox")
public class OrderTimeoutOutboxEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private String businessKey;
    private Long orderId;
    private Integer eventVersion;
    private String payload;
    private String status;
    private Integer sendAttempts;
    private LocalDateTime nextAttemptTime;
    private LocalDateTime sentAt;
    private LocalDateTime processedAt;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
