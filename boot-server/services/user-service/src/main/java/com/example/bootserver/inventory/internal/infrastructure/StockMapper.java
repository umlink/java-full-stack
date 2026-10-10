package com.example.bootserver.inventory.internal.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 锁定量可能由后续下单改变，因此调整总量必须在数据库重新检查锁定量。 */
public interface StockMapper extends BaseMapper<StockEntity> {

    @Update("""
            UPDATE t_stock SET total_count = #{total}, update_time = CURRENT_TIMESTAMP
            WHERE sku_id = #{skuId} AND locked_count <= #{total}
            """)
    int updateTotalIfEnough(@Param("skuId") Long skuId, @Param("total") long total);

    @Update("""
            UPDATE t_stock SET locked_count = locked_count + #{quantity}, update_time = CURRENT_TIMESTAMP
            WHERE sku_id = #{skuId} AND total_count - locked_count >= #{quantity}
            """)
    int reserveIfAvailable(@Param("skuId") Long skuId, @Param("quantity") int quantity);

    @Update("""
            UPDATE t_stock
            SET total_count = total_count - #{quantity},
                locked_count = locked_count - #{quantity},
                update_time = CURRENT_TIMESTAMP
            WHERE sku_id = #{skuId} AND locked_count >= #{quantity}
            """)
    int settleReserved(@Param("skuId") Long skuId, @Param("quantity") int quantity);

    @Update("""
            UPDATE t_stock
            SET locked_count = locked_count - #{quantity},
                update_time = CURRENT_TIMESTAMP
            WHERE sku_id = #{skuId} AND locked_count >= #{quantity}
            """)
    int releaseReserved(@Param("skuId") Long skuId, @Param("quantity") int quantity);
}
