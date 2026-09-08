package com.example.bootapp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.bootapp.entity.User;
import com.example.bootapp.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 冒烟测试：验证 Spring 上下文能启动 + H2 schema 初始化成功 + MyBatis-Plus 能查库。
 * <p>
 * 测试用内存库（jdbc:h2:mem），不污染开发用的 data/ 文件库。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:bootapp-test;MODE=MySQL",
        "spring.sql.init.mode=always"
})
class BootAppApplicationTests {

    @Autowired
    private UserMapper userMapper;

    @Test
    void contextLoads() {
        // Spring 上下文能启动 = 自动装配、数据源、MP 配置全部就位
    }

    @Test
    void h2SchemaInitializedAndQueryWorks() {
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .orderByAsc(User::getId));
        // schema.sql 预置了 3 条初始数据
        assertThat(users).hasSize(3);
        assertThat(users.get(0).getName()).isNotBlank();
    }
}
