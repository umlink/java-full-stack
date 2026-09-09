package com.example.bootserver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色实体 —— 对应 RBAC 的 t_role 表。
 * <p>
 * 注册成功后要把默认的 {@code USER} 角色绑定到新用户，随后 M1-06/08 登录与授权也会用到
 * 角色码这个稳定业务标识，因此这里单独为角色建模，而不是直接拼 SQL。
 */
@Data
@TableName("t_role")
public class Role {

    /** 主键：数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 稳定业务码（ADMIN / USER），授权判断依赖它而不是自增 ID */
    private String code;

    private String name;

    private LocalDateTime createTime;
}
