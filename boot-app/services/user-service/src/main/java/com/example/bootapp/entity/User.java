package com.example.bootapp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
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

    /** 主键：数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String email;

    private Integer age;

    /** 逻辑删除标记：0 正常 / 1 已删除 —— 删除接口实际执行 UPDATE 而非 DELETE（见全局配置） */
    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
