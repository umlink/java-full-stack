package com.example.bootserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码哈希工具配置 —— 把 {@link BCryptPasswordEncoder} 暴露为 Spring Bean。
 * <p>
 * BCrypt 是单向哈希 + 自动加盐：每次 encode 同一明文都会因随机盐产生不同密文，
 * 校验时则从密文里取出盐重新计算并比对。数据库只保存 60 字符左右的哈希串，
 * 即使库被拖走也无法还原明文。把编码器做成 Bean，注册与 M1-06 登录校验共用同一实现，
 * 避免每处 new 一个编码器（也便于将来替换为 Argon2 等其它算法）。
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
