package com.example.bootapp.service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.bootapp.entity.User;
import com.example.bootapp.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
 * 用户服务 —— 继承 MyBatis-Plus 的 {@link ServiceImpl} 获得基础 CRUD 能力。
 * <p>
 * {@code @Service} 让 Spring 容器管理这个 Bean（阶段零 05 讲「Bean 是什么」的实际例子）：
 * 单例、可被 Controller 构造器注入；业务规则以后写在这里。
 */
@Service
public class UserService extends ServiceImpl<UserMapper, User> {
}
