package com.example.bootserver.service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.bootserver.entity.User;
import com.example.bootserver.exception.UserNotFoundException;
import com.example.bootserver.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
 * 用户服务 —— 继承 MyBatis-Plus 的 {@link ServiceImpl} 获得基础 CRUD 能力。
 * <p>
 * {@code @Service} 让 Spring 容器管理这个 Bean（阶段零 05 讲「Bean 是什么」的实际例子）：
 * 单例、可被 Controller 构造器注入；业务规则以后写在这里。
 */
@Service
public class UserService extends ServiceImpl<UserMapper, User> {

    /**
     * 查询必须存在的用户。
     *
     * 查询结果为 {@code null} 时在业务层转换为用户域异常，Controller 因而只处理 HTTP 输入输出，
     * 不会在各个接口重复判断资源缺失。
     */
    public User getRequiredById(Long id) {
        User user = getById(id);
        if (user == null) {
            throw new UserNotFoundException();
        }
        return user;
    }
}
