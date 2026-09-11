package com.example.bootserver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体 —— 对应 t_user 表（阶段二 02 讲学过的「Bean + 注解」的实际例子）。
 * <p>
 * 列名映射：下划线转驼峰由 mybatis-plus 全局配置 map-underscore-to-camel-case 负责
 * （create_time ↔ createTime，无需逐字段声明）。
 */
@Data
@TableName("t_user")
public class User {

    /** 账号状态：正常。注册时显式赋值，避免依赖“insert 后实体字段仍为 null”的隐式默认值。 */
    public static final int STATUS_ACTIVE = 1;

    /** 账号状态：停用。停用账号不能登录，已有 JWT 也会在过滤器校验时失效。 */
    public static final int STATUS_DISABLED = 0;

    /** 逻辑删除状态：记录仍可追溯，但不应作为可用账号。 */
    public static final int NOT_DELETED = 0;

    /** 主键：数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 注册登录名：登录时以它定位账号；M0 用旧接口创建的用户该字段为 NULL，登录前需补全 */
    private String username;

    /** 展示名：注册时先用用户名填充，未来可由用户资料功能独立修改 */
    private String name;

    private String email;

    /** 密码哈希：只存 BCrypt 结果，禁止把明文密码放进实体（BCrypt 说明见 UserService.register） */
    // 实体会被用户查询接口直接序列化；哈希虽非明文，仍不能暴露给客户端以免成为离线撞库素材。
    @JsonIgnore
    private String passwordHash;

    /** 账号状态：见 {@link #STATUS_ACTIVE}；与逻辑删除 deleted 职责不同，deleted 标记行是否“删除” */
    private Integer status;

    private Integer age;

    /** 逻辑删除标记：0 正常 / 1 已删除 —— 删除接口实际执行 UPDATE 而非 DELETE（见全局配置） */
    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
