package com.example.bootserver.inventory.internal.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 锁定量可能由后续下单改变，因此调整总量必须在数据库重新检查锁定量。 */
public interface StockMapper extends BaseMapper<StockEntity> {

    @Select("""
            SELECT sku_id, total_count, locked_count, update_time
            FROM t_stock
            WHERE sku_id > #{afterSkuId}
            ORDER BY sku_id ASC
            LIMIT #{limit}
            """)
    List<StockEntity> selectBySkuIdAfter(@Param("afterSkuId") long afterSkuId,
                                         @Param("limit") int limit);

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
