package com.example.bootserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.bootserver.entity.Role;

/**
 * Role 的 Mapper —— 继承 {@link BaseMapper} 即获得单表 CRUD，无需手写 SQL。
 * 注册时用它按角色码查找 USER 角色；后续授权逻辑会继续复用。
 */
public interface RoleMapper extends BaseMapper<Role> {
}
