package com.example.bootserver.catalog.internal.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.bootserver.catalog.internal.infrastructure.persistence.Sku;

/** SKU 单表数据访问；业务编码的全生命周期唯一性由数据库约束负责。 */
public interface SkuMapper extends BaseMapper<Sku> {
}
