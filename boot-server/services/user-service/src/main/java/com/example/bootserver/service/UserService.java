package com.example.bootserver.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.bootserver.common.error.BusinessException;
import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.controller.dto.LoginRequest;
import com.example.bootserver.controller.dto.RegisterRequest;
import com.example.bootserver.controller.dto.UpdateUserRequest;
import com.example.bootserver.entity.Role;
import com.example.bootserver.security.RoleCodes;
import com.example.bootserver.entity.User;
import com.example.bootserver.entity.UserRole;
import com.example.bootserver.exception.UserNotFoundException;
import com.example.bootserver.mapper.RoleMapper;
import com.example.bootserver.mapper.UserMapper;
import com.example.bootserver.mapper.UserRoleMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户服务 —— 继承 MyBatis-Plus 的 {@link ServiceImpl} 获得基础 CRUD 能力。
 * <p>
 * {@code @Service} 让 Spring 容器管理这个 Bean（阶段零 05 讲「Bean 是什么」的实际例子）：
 * 单例、可被 Controller 构造器注入；业务规则以后写在这里。
 */
@Service
public class UserService extends ServiceImpl<UserMapper, User> {

    // 此哈希不是密钥或账号密码；用户不存在时仍执行一次 BCrypt，减小用户名存在性带来的时序差异。
    private static final String DUMMY_PASSWORD_HASH = "$2a$10$UDLudaZ5w6xai.L9/sNb..Q.cywghB4Il0Rw8jWLg7DSFbDjtEpg6";

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
     * 校验登录凭据，并返回通过认证的用户。
     *
     * 不论用户名不存在、密码不匹配还是账号停用，都返回同一未认证错误；否则攻击者可以通过
     * 响应差异枚举哪些账号存在。密码比较必须交给 BCrypt 的 matches，不能直接比较哈希字符串。
     */
    public User authenticate(LoginRequest request) {
        User user = lambdaQuery().eq(User::getUsername, request.username()).one();
        String passwordHash = user == null || user.getPasswordHash() == null
                ? DUMMY_PASSWORD_HASH : user.getPasswordHash();
        // 无论账号是否存在都执行一次 BCrypt，避免攻击者以明显更快的响应枚举用户名。
        boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);
        if (user == null
                || user.getStatus() == null
                || user.getStatus() != User.STATUS_ACTIVE
                || !passwordMatches) {
            throw invalidCredentials();
        }
        return user;
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

    /** 登录失败统一使用受控文案，调用方不能据此判断用户名是否存在。 */
    private BusinessException invalidCredentials() {
        return new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
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
     * ADMIN 只能由受控管理流程分配，注册接口永远不开放指定角色。
     */
    private void bindDefaultUserRole(Long userId) {
        // 用角色码定位主键，避免依赖每次启动可能变化的自增 ID
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getCode, RoleCodes.USER));
        if (role == null) {
            // 不属于客户端可见的业务失败：初始数据缺失属于服务端配置错误，交全局处理器按 500 兜底
            throw new IllegalStateException(RoleCodes.USER + " 角色不存在，请检查 schema.sql 初始数据");
        }

        // 关联表没有唯一约束外的其它逻辑，直接写两列即可；user_id 取自刚回填的自增主键
        UserRole binding = new UserRole();
        binding.setUserId(userId);
        binding.setRoleId(role.getId());
        userRoleMapper.insert(binding);
    }

    /**
     * 本地管理员引导（M1-C-05）：确保指定账号存在并绑定 ADMIN 角色，供启动引导器调用。
     * <p>
     * 这是「受控管理流程」的本地形态：账号不存在时按注册同规则创建（BCrypt 哈希、状态正常、
     * 展示名先用用户名），已存在则只补缺失的 ADMIN 关联——幂等，重复启动不重复建号也不重复绑定，
     * 不覆盖已有学习数据。只绑 ADMIN 角色保持最小权限；密码仅在内存中经 BCrypt 后落库，
     * 不进入任何日志。生产环境不注入引导变量即不会执行本方法。
     *
     * @return true 表示本次新建了账号；false 表示账号已存在、仅确认角色关联
     */
    @Transactional
    public boolean ensureLocalAdmin(String username, String password) {
        // 不能用 MyBatis-Plus 默认查询：它会隐藏 deleted=1 的同名记录，随后 INSERT 会落到难懂的唯一键异常。
        User user = baseMapper.selectByUsernameIncludingDeleted(username);
        boolean created = false;
        if (user == null) {
            user = new User();
            user.setUsername(username);
            user.setName(username);
            // 引导账号没有真实邮箱语义，但 t_user.email 非空且唯一：按登录名生成稳定的占位邮箱
            user.setEmail(username + "@local.test");
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setStatus(User.STATUS_ACTIVE);
            save(user);
            created = true;
        } else if (!isActiveAccount(user)) {
            throw new IllegalStateException("本地管理员账号 %s 已%s，请更换 app.local-admin.username 或先修复该账号状态"
                    .formatted(username, describeUnavailableAccount(user)));
        }
        bindAdminRoleIfAbsent(user.getId());
        return created;
    }

    /** 绑定 ADMIN 角色；已绑定时不再重复插入，保证引导在多次启动间幂等。 */
    private void bindAdminRoleIfAbsent(Long userId) {
        Role adminRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getCode, RoleCodes.ADMIN));
        if (adminRole == null) {
            throw new IllegalStateException(RoleCodes.ADMIN + " 角色不存在，请检查 schema.sql 初始数据");
        }

        long bound = userRoleMapper.countByUserIdAndRoleId(userId, adminRole.getId());
        if (bound > 0) {
            return;
        }

        UserRole binding = new UserRole();
        binding.setUserId(userId);
        binding.setRoleId(adminRole.getId());
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

    /** 管理列表按主键稳定排序，避免分页在未指定排序时因数据库执行计划变化而跳项或重复。 */
    public List<User> listUsers() {
        return list(new LambdaQueryWrapper<User>().orderByAsc(User::getId));
    }

    /** 管理分页限制在 Controller 校验后的范围内，Service 只负责查询意图。 */
    public Page<User> listUsersByPage(long page, long size) {
        return page(new Page<>(page, size), new LambdaQueryWrapper<User>().orderByAsc(User::getId));
    }

    /** 以展示名模糊检索时同样使用稳定排序，使相同条件的结果顺序可预测。 */
    public List<User> searchUsersByName(String name) {
        return list(new LambdaQueryWrapper<User>().like(User::getName, name).orderByAsc(User::getId));
    }

    /**
     * 更新管理员可维护的基础资料。
     *
     * 先确认资源存在，再从 DTO 白名单逐项映射；请求体中的 status、deleted 等字段没有入口，
     * 因而不会被 MyBatis-Plus 的 updateById 意外写入数据库。
     */
    public boolean updateUserProfile(Long id, UpdateUserRequest request) {
        getRequiredById(id);
        if (!request.hasUpdateField()) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "至少提供一个可更新字段");
        }

        User user = new User();
        user.setId(id);
        user.setName(request.name());
        user.setEmail(request.email());
        user.setAge(request.age());
        return updateById(user);
    }

    /** 删除前统一确认资源存在，使删除、详情和更新对不存在用户都返回同一 404 语义。 */
    public boolean deleteUserById(Long id) {
        getRequiredById(id);
        return removeById(id);
    }

    private boolean isActiveAccount(User user) {
        return Integer.valueOf(User.STATUS_ACTIVE).equals(user.getStatus())
                && Integer.valueOf(User.NOT_DELETED).equals(user.getDeleted());
    }

    private String describeUnavailableAccount(User user) {
        if (!Integer.valueOf(User.NOT_DELETED).equals(user.getDeleted())) {
            return "逻辑删除";
        }
        if (!Integer.valueOf(User.STATUS_ACTIVE).equals(user.getStatus())) {
            return "停用";
        }
        return "异常";
    }
}
