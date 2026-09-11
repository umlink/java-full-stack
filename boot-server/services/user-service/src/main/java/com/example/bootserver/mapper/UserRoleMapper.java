package com.example.bootserver.mapper;

import com.example.bootserver.entity.UserRole;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户-角色关联表 Mapper —— 注册事务里用它写入默认角色绑定。
 *
 * 表的主键由 user_id 与 role_id 共同组成，不适合假装成具有单一主键的 BaseMapper；显式 SQL
 * 让写入和按用户查询的条件完整可见，也消除 MyBatis-Plus 对复合主键的运行时警告。
 */
public interface UserRoleMapper {

    @Insert("""
            INSERT INTO t_user_role (user_id, role_id)
            VALUES (#{userId}, #{roleId})
            """)
    int insert(UserRole userRole);

    @Select("""
            SELECT user_id, role_id
            FROM t_user_role
            WHERE user_id = #{userId}
            """)
    List<UserRole> selectByUserId(@Param("userId") Long userId);

    /** 指定用户与角色的关联是否已存在：本地管理员引导用它实现幂等绑定 */
    @Select("""
            SELECT COUNT(*)
            FROM t_user_role
            WHERE user_id = #{userId} AND role_id = #{roleId}
            """)
    long countByUserIdAndRoleId(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
