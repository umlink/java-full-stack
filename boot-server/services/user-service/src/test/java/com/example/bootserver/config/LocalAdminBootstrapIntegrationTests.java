package com.example.bootserver.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.bootserver.entity.Role;
import com.example.bootserver.security.RoleCodes;
import com.example.bootserver.entity.User;
import com.example.bootserver.common.result.Result;
import com.example.bootserver.mapper.RoleMapper;
import com.example.bootserver.mapper.UserMapper;
import com.example.bootserver.mapper.UserRoleMapper;
import com.example.bootserver.service.UserService;
import com.example.bootserver.security.BearerAuthentication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本地管理员引导集成测试（M1-C-05）：
 * 引导在应用启动后自动执行，因此必须用真实上下文属性驱动，经真实 HTTP 验证登录与授权结果。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:local-admin-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.sql.init.mode=always",
                "app.local-admin.username=boot-admin",
                "app.local-admin.password=secret123"
        }
)
class LocalAdminBootstrapIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ServletPathProperties servletPathProperties;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Test
    void bootstrapCreatesConfiguredAdministratorWithUserManagePermission() throws Exception {
        // 启动即引导：账号可直接登录，并且 ADMIN 角色关联的 user:manage 让用户管理资源放行。
        String accessToken = loginAsBootAdmin();

        HttpResponse<String> users = get(externalPath("/users"), accessToken);
        assertThat(users.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(users.body()).contains("\"code\":" + Result.SUCCESS_CODE);
    }

    @Test
    void bootstrapIsIdempotentWhenAccountAlreadyExists() throws Exception {
        // 显式重复执行引导：不重复建号、不重复绑定角色，登录与授权结果不变
        boolean created = userService.ensureLocalAdmin("boot-admin", "secret123");
        assertThat(created).isFalse();

        assertThat(countUsersByUsername("boot-admin")).isEqualTo(1);
        assertThat(countAdminRoleBindings("boot-admin")).isEqualTo(1);

        HttpResponse<String> users = get(externalPath("/users"), loginAsBootAdmin());
        assertThat(users.statusCode()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void bootstrapSkipsWhenNotConfigured() {
        // 未配置（两个字段都空）时引导器不触碰数据库：生产默认形态
        LocalAdminProperties blankProperties = new LocalAdminProperties();
        LocalAdminInitializer initializer = new LocalAdminInitializer(blankProperties, userService);

        long usersBefore = userService.count();
        initializer.run(null);

        assertThat(userService.count()).isEqualTo(usersBefore);
    }

    @Test
    void bootstrapFailsClearlyWhenConfiguredUsernameIsDisabled() {
        Long userId = userService.register(new com.example.bootserver.controller.dto.RegisterRequest(
                "disabled-bootstrap-admin", "disabled-bootstrap-admin@example.com", "secret123"));
        User disabled = new User();
        disabled.setId(userId);
        disabled.setStatus(User.STATUS_DISABLED);
        assertThat(userMapper.updateById(disabled)).isEqualTo(1);

        assertThatThrownBy(() -> userService.ensureLocalAdmin("disabled-bootstrap-admin", "secret123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已停用")
                .hasMessageContaining("app.local-admin.username");
    }

    @Test
    void bootstrapFailsClearlyWhenConfiguredUsernameIsLogicallyDeleted() {
        Long userId = userService.register(new com.example.bootserver.controller.dto.RegisterRequest(
                "deleted-bootstrap-admin", "deleted-bootstrap-admin@example.com", "secret123"));
        assertThat(userMapper.deleteById(userId)).isEqualTo(1);

        assertThatThrownBy(() -> userService.ensureLocalAdmin("deleted-bootstrap-admin", "secret123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已逻辑删除")
                .hasMessageContaining("app.local-admin.username");
    }

    private String loginAsBootAdmin() throws Exception {
        HttpResponse<String> login = postJson(externalPath("/auth/login"),
                "{\"username\":\"boot-admin\",\"password\":\"secret123\"}");
        assertThat(login.statusCode()).isEqualTo(HttpStatus.OK.value());
        return objectMapper.readTree(login.body()).path("data").path("accessToken").asString();
    }

    private long countUsersByUsername(String username) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    private long countAdminRoleBindings(String username) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        Role adminRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, RoleCodes.ADMIN));
        return userRoleMapper.countByUserIdAndRoleId(user.getId(), adminRole.getId());
    }

    private HttpResponse<String> get(String path, String accessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header(HttpHeaders.AUTHORIZATION, BearerAuthentication.SCHEME_PREFIX + accessToken)
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String externalPath(String resourcePath) {
        return servletPathProperties.toExternalPath(resourcePath);
    }
}
