package com.example.bootserver.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 本地管理员引导的运行时配置（M1-C-05）。
 *
 * 两个字段默认留空 = 功能关闭：不设置环境变量就不创建任何管理员，生产环境天然安全。
 * 只在本地验收时通过 `BOOT_ADMIN_USERNAME` / `BOOT_ADMIN_PASSWORD` 同时注入，
 * 密码只存在于本地进程内存与 BCrypt 哈希中，不写入仓库或文档示例。
 */
@Validated
@ConfigurationProperties(prefix = "app.local-admin")
public class LocalAdminProperties {

    /** 引导的管理员登录名；留空表示不启用引导 */
    private String username = "";

    /** 引导管理员的初始密码；必须与 username 同时提供或同时留空 */
    private String password = "";

    /**
     * 配置绑定校验：半配置（只填一项）直接拒绝启动，
     * 避免引导器拿着用户名和空密码创建出无法登录的账号。
     */
    @AssertTrue(message = "app.local-admin 的 username 与 password 必须同时提供或同时留空")
    public boolean isUsernamePasswordPaired() {
        boolean usernameBlank = username == null || username.isBlank();
        boolean passwordBlank = password == null || password.isBlank();
        return usernameBlank == passwordBlank;
    }

    /** 引导是否启用：两个字段都有值时才执行账号准备 */
    public boolean isEnabled() {
        return username != null && !username.isBlank() && password != null && !password.isBlank();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
