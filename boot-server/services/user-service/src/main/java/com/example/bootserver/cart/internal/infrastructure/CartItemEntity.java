package com.example.bootserver.cart.internal.infrastructure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** t_cart_item 的数据库映射，不作为接口 DTO 或领域对象使用。 */
@Data
@TableName("t_cart_item")
public class CartItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long skuId;
    private Integer quantity;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
