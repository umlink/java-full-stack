package com.example.bootserver.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.bootserver.common.error.BusinessException;
import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.controller.dto.RegisterRequest;
import com.example.bootserver.entity.Role;
import com.example.bootserver.entity.User;
import com.example.bootserver.entity.UserRole;
import com.example.bootserver.exception.UserNotFoundException;
import com.example.bootserver.mapper.RoleMapper;
import com.example.bootserver.mapper.UserMapper;
import com.example.bootserver.mapper.UserRoleMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户服务 —— 继承 MyBatis-Plus 的 {@link ServiceImpl} 获得基础 CRUD 能力。
 * <p>
 * {@code @Service} 让 Spring 容器管理这个 Bean（阶段零 05 讲「Bean 是什么」的实际例子）：
 * 单例、可被 Controller 构造器注入；业务规则以后写在这里。
 */
@Service
public class UserService extends ServiceImpl<UserMapper, User> {

    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(RoleMapper roleMapper, UserRoleMapper userRoleMapper, PasswordEncoder passwordEncoder) {
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 注册 —— 新用户默认绑定 USER 角色，密码只保存 BCrypt 哈希。
     * <p>
     * {@code @Transactional} 让「插入用户」和「绑定角色」两个写操作在同一数据库事务内：
     * 只要其中一个失败，另一个也回滚，避免出现「有账号却没角色」的中间态。事务由 Spring
     * 拦截器通过 {@code DataSourceTransactionManager} 开启/提交/回滚，方法抛异常即回滚。
     * <p>
     * 事务里发生并发注册时，业务预检查可能都被放行，此时数据库唯一约束成为最后防线；
     * 后一个 INSERT 会违反唯一约束，再由全局处理器统一转换为 409/40900，保证库中绝不出现重复键。
     */
    @Transactional
    public Long register(RegisterRequest request) {
        // 业务预检查：先拦下常规重复请求，给客户端明确的 409/40900，而不是靠数据库报错
        assertUsernameAvailable(request.username());
        assertEmailAvailable(request.email());

        User user = new User();
        user.setUsername(request.username());
        // t_user.name 是 M0 遗留的 NOT NULL 字段，注册时先用用户名充当展示名
        user.setName(request.username());
        user.setEmail(request.email());
        // BCrypt 单次哈希不可逆且自动加盐：这里入库的只是哈希串，任何日志/备份都没有明文
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(User.STATUS_ACTIVE);

        save(user);

        // save 之后无需再次查询：IdType.AUTO 让数据库生成主键，MyBatis-Plus 通过 JDBC
        // getGeneratedKeys 把自增值回填到 user 对象，user.getId() 就是新记录的数据库 ID。
        bindDefaultUserRole(user.getId());

        return user.getId();
    }

    /**
     * 用户名唯一性校验：按稳定业务键查询已存在记录，重复即抛业务冲突。
     */
    private void assertUsernameAvailable(String username) {
        Long count = lambdaQuery().eq(User::getUsername, username).count();
        if (count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已注册");
        }
    }

    /**
     * 邮箱唯一性校验：与用户名一样，常规重复在这里被转成可理解的 409 冲突。
     */
    private void assertEmailAvailable(String email) {
        Long count = lambdaQuery().eq(User::getEmail, email).count();
        if (count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "邮箱已注册");
        }
    }

    /**
     * 绑定默认 USER 角色：注册不开放指定角色，普通用户永远只能拿到 USER，
     * ADMIN 只能由后续管理功能（M1-08）在受控流程里分配。
     */
    private void bindDefaultUserRole(Long userId) {
        // 用角色码定位主键，避免依赖每次启动可能变化的自增 ID
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getCode, "USER"));
        if (role == null) {
            // 不属于客户端可见的业务失败：初始数据缺失属于服务端配置错误，交全局处理器按 500 兜底
            throw new IllegalStateException("USER 角色不存在，请检查 schema.sql 初始数据");
        }

        // 关联表没有唯一约束外的其它逻辑，直接写两列即可；user_id 取自刚回填的自增主键
        UserRole binding = new UserRole();
        binding.setUserId(userId);
        binding.setRoleId(role.getId());
        userRoleMapper.insert(binding);
    }

    /**
     * 查询必须存在的用户。
     *
     * 查询结果为 {@code null} 时在业务层转换为用户域异常，Controller 因而只处理 HTTP 输入输出，
     * 不会在各个接口重复判断资源缺失。
     */
    public User getRequiredById(Long id) {
        User user = getById(id);
        if (user == null) {
            throw new UserNotFoundException();
        }
        return user;
    }
}
