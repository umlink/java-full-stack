package com.example.bootserver.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户-角色关联实体 —— 对应多对多表 t_user_role。
 * <p>
 * 表使用复合主键 (user_id, role_id)，没有单列自增主键，因此不需要 {@code @TableId}；
 * MyBatis-Plus 的 insert 只负责把两列写进关联表，查询时也按这两列匹配。
 */
@Data
@TableName("t_user_role")
public class UserRole {

    private Long userId;

    private Long roleId;
}
