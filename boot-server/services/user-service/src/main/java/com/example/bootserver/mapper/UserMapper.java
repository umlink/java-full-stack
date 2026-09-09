package com.example.bootserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.bootserver.entity.User;

/**
 * User 的 Mapper —— 继承 {@link BaseMapper} 即获得单表 CRUD（无需手写 SQL）。
 * <p>
 * 这是一个接口不是类：MyBatis 在启动时为其生成代理实现（阶段二 01 讲「框架魔法」的实例），
 * 复杂查询再在这里声明自定义 SQL 方法。
 */
public interface UserMapper extends BaseMapper<User> {
}
