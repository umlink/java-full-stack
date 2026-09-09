package com.example.bootserver.entity;

import lombok.Data;

/**
 * 用户-角色关联实体 —— 对应多对多表 t_user_role。
 * <p>
 * 表使用复合主键 (user_id, role_id)，没有单列自增主键；因此由 UserRoleMapper 显式声明
 * 复合条件 SQL，而不是套用以单主键实体为前提的通用 Mapper。
 */
@Data
public class UserRole {

    private Long userId;

    private Long roleId;
}
