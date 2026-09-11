package com.example.bootserver;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.bootserver.entity.User;
import com.example.bootserver.security.PermissionCodes;
import com.example.bootserver.security.RoleCodes;
import com.example.bootserver.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
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
class BootServerApplicationTests {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

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

    @Test
    void rbacInitialRolesAndAdminPermissionAreAvailable() {
        // 角色码和权限码是后续授权判断的稳定业务标识，不能依赖每次启动可能不同的自增 ID。
        List<String> roleCodes = jdbcTemplate.queryForList(
                "SELECT code FROM t_role ORDER BY code", String.class);
        // SQL 只格式化编译期业务常量，绝不接收外部输入，避免把协议码重复为第二份字面量定义。
        Integer adminManagePermissionCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM t_role role
                JOIN t_role_permission relation ON relation.role_id = role.id
                JOIN t_permission permission ON permission.id = relation.permission_id
                WHERE role.code = '%s' AND permission.code = '%s'
                """.formatted(RoleCodes.ADMIN, PermissionCodes.USER_MANAGEMENT), Integer.class);

        assertThat(roleCodes).containsExactly(RoleCodes.ADMIN, RoleCodes.USER);
        assertThat(adminManagePermissionCount).isEqualTo(1);
    }

    @Test
    void rerunningSchemaDoesNotDuplicateRbacInitialData() throws Exception {
        int roleCountBefore = count("t_role");
        int permissionCountBefore = count("t_permission");
        int relationCountBefore = count("t_role_permission");

        // 手动再执行一次与应用启动相同的脚本，验证 IF NOT EXISTS 与业务唯一键守卫的实际效果。
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/schema.sql"));
        }

        assertThat(count("t_role")).isEqualTo(roleCountBefore);
        assertThat(count("t_permission")).isEqualTo(permissionCountBefore);
        assertThat(count("t_role_permission")).isEqualTo(relationCountBefore);
    }

    private int count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }
}
