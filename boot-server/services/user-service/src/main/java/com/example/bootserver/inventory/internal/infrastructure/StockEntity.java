package com.example.bootserver.inventory.internal.infrastructure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** t_stock 数据库映射，不作为领域对象或接口响应。 */
@Data
@TableName("t_stock")
public class StockEntity {

    @TableId(type = IdType.INPUT)
    private Long skuId;
    private Long totalCount;
    private Long lockedCount;
    private LocalDateTime updateTime;
}
