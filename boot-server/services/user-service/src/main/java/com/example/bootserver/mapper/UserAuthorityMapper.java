package com.example.bootserver.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 读取当前用户有效权限的专用查询 Mapper。
 *
 * 授权需要跨用户角色、角色权限和权限三张表，放在独立 Mapper 可让 UserRoleMapper 继续只负责关联表写入。
 */
public interface UserAuthorityMapper {

    /** 按权限码去重，避免用户拥有多个角色时向 SecurityContext 写入重复 authority。 */
    @Select("""
            SELECT DISTINCT permission.code
            FROM t_user_role user_role
            JOIN t_role_permission role_permission ON role_permission.role_id = user_role.role_id
            JOIN t_permission permission ON permission.id = role_permission.permission_id
            WHERE user_role.user_id = #{userId}
            """)
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);
}
