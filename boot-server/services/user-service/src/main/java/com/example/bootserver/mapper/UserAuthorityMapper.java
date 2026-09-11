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

    /**
     * 认证前确认 JWT 主体仍是可用账号。
     *
     * JWT 只证明令牌曾经由本服务签发；账号被停用或逻辑删除后，旧令牌必须立即失效，
     * 因此不能仅凭令牌中的用户 ID 创建 SecurityContext。
     */
    @Select("""
            SELECT account.id
            FROM t_user account
            WHERE account.id = #{userId}
              AND account.status = #{activeStatus}
              AND account.deleted = #{notDeleted}
              """)
    Long selectActiveUserIdById(@Param("userId") Long userId,
                                @Param("activeStatus") int activeStatus,
                                @Param("notDeleted") int notDeleted);

    /** 按权限码去重，避免用户拥有多个角色时向 SecurityContext 写入重复 authority。 */
    @Select("""
            SELECT DISTINCT permission.code
            FROM t_user_role user_role
            JOIN t_user account ON account.id = user_role.user_id
            JOIN t_role_permission role_permission ON role_permission.role_id = user_role.role_id
            JOIN t_permission permission ON permission.id = role_permission.permission_id
            WHERE user_role.user_id = #{userId}
              AND account.status = #{activeStatus}
              AND account.deleted = #{notDeleted}
              """)
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId,
                                               @Param("activeStatus") int activeStatus,
                                               @Param("notDeleted") int notDeleted);
}
