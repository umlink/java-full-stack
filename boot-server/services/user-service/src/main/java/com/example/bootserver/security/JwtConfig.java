package com.example.bootserver.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 启用 JWT 配置绑定。
 *
 * Spring 启动时会把 application.yml 与环境变量合并后绑定到 JwtProperties；密钥缺失会在服务接流量前失败。
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {
}
