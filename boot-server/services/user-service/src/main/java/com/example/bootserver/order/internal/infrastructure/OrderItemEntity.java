package com.example.bootserver.order.internal.infrastructure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/** t_order_item 保存下单时单价，而非查询时的当前售价。 */
@Data
@TableName("t_order_item")
public class OrderItemEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long skuId;
    private Integer quantity;
    private BigDecimal price;
}
