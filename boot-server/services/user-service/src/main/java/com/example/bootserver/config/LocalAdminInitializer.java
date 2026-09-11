package com.example.bootserver.config;

import com.example.bootserver.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 本地管理员引导（M1-C-05）：应用就绪后确保本地验收用的 ADMIN 账号可用。
 *
 * {@link ApplicationRunner} 在应用完全启动后执行一次；是否启用由
 * {@code app.local-admin.*} 配置决定——默认留空即静默跳过，生产环境不注入变量就不会创建任何管理员。
 * 引导结果只记录用户名与「新建/复用」，密码绝不进入日志。
 */
@Component
@EnableConfigurationProperties(LocalAdminProperties.class)
public class LocalAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalAdminInitializer.class);

    private final LocalAdminProperties properties;
    private final UserService userService;

    public LocalAdminInitializer(LocalAdminProperties properties, UserService userService) {
        this.properties = properties;
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }

        boolean created = userService.ensureLocalAdmin(properties.getUsername(), properties.getPassword());
        log.info("本地管理员引导完成：账号 {}，{}", properties.getUsername(),
                created ? "已创建并绑定 ADMIN 角色" : "已存在，确认 ADMIN 角色关联");
    }
}
