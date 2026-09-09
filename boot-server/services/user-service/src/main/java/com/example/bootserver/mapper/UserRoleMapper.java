package com.example.bootserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.bootserver.entity.UserRole;

/**
 * 用户-角色关联表 Mapper —— 注册事务里用它写入默认角色绑定。
 * 关联表没有独立主键，BaseMapper 的 insert/select 均以 user_id + role_id 表达。
 */
public interface UserRoleMapper extends BaseMapper<UserRole> {
}
