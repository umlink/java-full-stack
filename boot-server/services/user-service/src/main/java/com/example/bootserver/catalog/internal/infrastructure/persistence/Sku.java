package com.example.bootserver.catalog.internal.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** SKU 持久化实体；skuCode 在逻辑删除后仍由数据库保持唯一。 */
@Data
@TableName("t_sku")
public class Sku {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String skuCode;
    private BigDecimal price;
    @Version
    private Integer version;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
