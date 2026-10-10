package com.example.bootserver.catalog.internal.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.bootserver.catalog.internal.infrastructure.persistence.Product;

/** 商品单表数据访问；查询默认遵循 MyBatis-Plus 逻辑删除过滤。 */
public interface ProductMapper extends BaseMapper<Product> {
}
