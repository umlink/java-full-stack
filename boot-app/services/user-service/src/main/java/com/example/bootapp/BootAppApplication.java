package com.example.bootapp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用入口 —— 对齐阶段二 02 讲「最简可运行示例」。
 * <p>
 * {@code @SpringBootApplication} = 配置类 + 组件扫描 + 自动装配三个注解的组合；
 * 启动后内嵌 Tomcat 监听 8080，应用启动时自动执行 schema.sql 完成建表。
 */
@SpringBootApplication
@MapperScan("com.example.bootapp.mapper")
public class BootAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(BootAppApplication.class, args);
    }
}
