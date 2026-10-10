package com.example.bootserver.identity.internal.application.command;

import com.example.bootserver.common.error.BusinessException;
import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.identity.internal.domain.IdentityRepository;
import com.example.bootserver.identity.internal.domain.RoleCodes;
import com.example.bootserver.identity.internal.domain.UserAccount;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 身份命令服务；只编排领域模型、端口和事务。 */
@Service
public class UserService {

    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$UDLudaZ5w6xai.L9/sNb..Q.cywghB4Il0Rw8jWLg7DSFbDjtEpg6";

    private final IdentityRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserService(IdentityRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    /** 新用户与默认 USER 角色在同一事务内创建。 */
    @Transactional
    public Long register(RegisterCommand command) {
        if (repository.usernameExists(command.username())) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已注册");
        }
        if (repository.emailExists(command.email())) {
            throw new BusinessException(ErrorCode.CONFLICT, "邮箱已注册");
        }
        UserAccount account = repository.insert(UserAccount.registered(
                command.username(), command.email(), passwordEncoder.encode(command.password())));
        repository.bindRole(account.id(), RoleCodes.USER);
        return account.id();
    }

    /** 校验凭据并返回用户 ID；所有失败分支使用同一外部语义。 */
    public Long authenticate(LoginCommand command) {
        UserAccount account = repository.findByUsername(command.username());
        String passwordHash = account == null || account.passwordHash() == null
                ? DUMMY_PASSWORD_HASH : account.passwordHash();
        boolean passwordMatches = passwordEncoder.matches(command.password(), passwordHash);
        if (account == null || !account.isActive() || !passwordMatches) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        return account.id();
    }

    /** M0 兼容入口：创建没有登录凭据的资料记录。 */
    public Long createUser(CreateUserCommand command) {
        return repository.insert(UserAccount.legacy(command.name(), command.email(), command.age())).id();
    }

    public boolean updateUserProfile(Long id, UpdateUserCommand command) {
        requireUser(id);
        if (!command.hasUpdateField()) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "至少提供一个可更新字段");
        }
        return repository.updateProfile(id, command.name(), command.email(), command.age());
    }

    public boolean deleteUserById(Long id) {
        requireUser(id);
        return repository.deleteById(id);
    }

    /** 本地管理员引导保持幂等，不覆盖已有账号资料。 */
    @Transactional
    public boolean ensureLocalAdmin(String username, String password) {
        UserAccount account = repository.findByUsernameIncludingDeleted(username);
        boolean created = false;
        if (account == null) {
            account = repository.insert(UserAccount.registered(
                    username, username + "@local.test", passwordEncoder.encode(password)));
            created = true;
        } else if (!account.isActive()) {
            throw new IllegalStateException("本地管理员账号 %s 已%s，请更换 app.local-admin.username 或先修复该账号状态"
                    .formatted(username, describeUnavailableAccount(account)));
        }
        repository.bindRoleIfAbsent(account.id(), RoleCodes.ADMIN);
        return created;
    }

    private UserAccount requireUser(Long id) {
        UserAccount account = repository.findById(id);
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return account;
    }

    private String describeUnavailableAccount(UserAccount account) {
        if (!Integer.valueOf(UserAccount.NOT_DELETED).equals(account.deleted())) {
            return "逻辑删除";
        }
        if (!Integer.valueOf(UserAccount.STATUS_ACTIVE).equals(account.status())) {
            return "停用";
        }
        return "异常";
    }
}
