package com.example.bootserver.cart.internal.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

/** 购物车 SQL：条件更新与唯一约束共同保证并发下不丢失增量。 */
public interface CartItemMapper extends BaseMapper<CartItemEntity> {

    @Update("""
            UPDATE t_cart_item SET quantity = quantity + #{delta}, update_time = CURRENT_TIMESTAMP
            WHERE user_id = #{userId} AND sku_id = #{skuId} AND quantity <= #{maxQuantity} - #{delta}
            """)
    int increase(@Param("userId") Long userId, @Param("skuId") Long skuId,
                 @Param("delta") int delta, @Param("maxQuantity") int maxQuantity);

    @Update("""
            UPDATE t_cart_item SET quantity = #{quantity}, update_time = CURRENT_TIMESTAMP
            WHERE id = #{itemId} AND user_id = #{userId}
            """)
    int replace(@Param("userId") Long userId, @Param("itemId") Long itemId,
                @Param("quantity") int quantity);

    @Delete("DELETE FROM t_cart_item WHERE id = #{itemId} AND user_id = #{userId}")
    int deleteOwned(@Param("userId") Long userId, @Param("itemId") Long itemId);

    @Delete("DELETE FROM t_cart_item WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

}
