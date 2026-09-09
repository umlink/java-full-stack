package com.example.bootserver.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.bootserver.common.error.BusinessException;
import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.controller.dto.RegisterRequest;
import com.example.bootserver.entity.Role;
import com.example.bootserver.entity.User;
import com.example.bootserver.entity.UserRole;
import com.example.bootserver.mapper.RoleMapper;
import com.example.bootserver.mapper.UserMapper;
import com.example.bootserver.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 注册流程的集成测试：走真实 H2 内存库 + MyBatis-Plus，验证密码哈希落库、
 * 唯一性冲突、默认 USER 角色绑定和密码加盐，防止这些关键不变量在层间传递时被绕过。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:register-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
class RegisterFlowIntegrationTests {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private DataSource dataSource;

    @Test
    void registerStoresBcryptHashAndBindsOnlyUserRole() {
        Long userId = userService.register(new RegisterRequest("dave", "dave@example.com", "secret123"));

        User saved = userMapper.selectById(userId);
        // 核心不变量：库中不允许出现明文密码，保存的是 BCrypt 哈希串
        assertThat(saved.getPasswordHash()).startsWith("$2");
        assertThat(saved.getPasswordHash()).isNotEqualTo("secret123");
        assertThat(saved.getStatus()).isEqualTo(User.STATUS_ACTIVE);

        // 默认只绑定 USER 角色；请求体没有 role 字段，普通用户永远拿不到 ADMIN
        Role userRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, "USER"));
        List<Long> boundRoleIds = findRoleIdsByUserId(userId);
        assertThat(boundRoleIds).containsExactly(userRole.getId());
    }

    @Test
    void registerRejectsDuplicateUsername() {
        userService.register(new RegisterRequest("dave2", "dave2@example.com", "secret123"));

        assertThatThrownBy(() -> userService.register(
                new RegisterRequest("dave2", "another@example.com", "secret456")))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException businessError = (BusinessException) error;
                    assertThat(businessError.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(businessError.getMessage()).isEqualTo("用户名已注册");
                });
    }

    @Test
    void registerRejectsDuplicateEmail() {
        userService.register(new RegisterRequest("dave3", "dave3@example.com", "secret123"));

        assertThatThrownBy(() -> userService.register(
                new RegisterRequest("dave4", "dave3@example.com", "secret456")))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException businessError = (BusinessException) error;
                    assertThat(businessError.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(businessError.getMessage()).isEqualTo("邮箱已注册");
                });
    }

    @Test
    void legacyCreateUserStillWorksWithoutAuthFields() {
        // 复现 M0 的创建方式：只给 name/email/age，不给注册字段。
        // 新 schema 允许 username/password_hash 为 NULL、status 用数据库默认值，
        // 因此旧接口不会因新增列而失败。
        User legacy = new User();
        legacy.setName("Legacy Dave");
        legacy.setEmail("legacy-dave@example.com");
        legacy.setAge(40);
        userService.save(legacy);

        // save 后 getId() 已被数据库自增主键回填，无需再次查询
        User saved = userMapper.selectById(legacy.getId());
        assertThat(saved.getUsername()).isNull();
        assertThat(saved.getPasswordHash()).isNull();
        assertThat(saved.getStatus()).isEqualTo(User.STATUS_ACTIVE);
    }

    @Test
    void samePasswordProducesDifferentHashesDueToBcryptSalt() {
        Long firstId = userService.register(new RegisterRequest("salt1", "salt1@example.com", "same-password"));
        Long secondId = userService.register(new RegisterRequest("salt2", "salt2@example.com", "same-password"));

        String firstHash = userMapper.selectById(firstId).getPasswordHash();
        String secondHash = userMapper.selectById(secondId).getPasswordHash();
        // BCrypt 自动加盐：相同明文每次加密结果都不同，防止两张表哈希相同被反向匹配
        assertThat(firstHash).isNotEqualTo(secondHash);
        assertThat(firstHash).startsWith("$2");
    }

    @Test
    void rerunningSchemaKeepsRegistrationUsable() throws Exception {
        int userCountBefore = (int) userService.count();

        // 与应用启动相同的脚本再执行一次：IF NOT EXISTS 与业务唯一键守卫应为幂等
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/schema.sql"));
        }

        assertThat(userService.count()).isEqualTo(userCountBefore);

        // 幂等后的库仍然能完成注册，说明新增列与唯一约束没有被重复执行破坏
        Long userId = userService.register(new RegisterRequest("idem1", "idem1@example.com", "secret123"));
        assertThat(userMapper.selectById(userId).getPasswordHash()).startsWith("$2");
    }

    private List<Long> findRoleIdsByUserId(Long userId) {
        return userRoleMapper.selectByUserId(userId)
                .stream()
                .map(UserRole::getRoleId)
                .toList();
    }
}
