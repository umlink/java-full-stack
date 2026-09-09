package com.example.bootserver;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * 应用入口 —— 对齐阶段二 02 讲「最简可运行示例」。
 * <p>
 * {@code @SpringBootApplication} = 配置类 + 组件扫描 + 自动装配三个注解的组合；
 * 启动后内嵌 Tomcat 监听 8080，应用启动时自动执行 schema.sql 完成建表。
 */
// JWT 过滤器不使用 UserDetailsService；排除默认内存用户，避免启动时生成无实际用途的随机密码。
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@MapperScan("com.example.bootserver.mapper")
public class BootServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(BootServerApplication.class, args);
    }
}
