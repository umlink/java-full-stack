package com.example.bootserver.identity.internal.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.bootserver.identity.internal.domain.IdentityRepository;
import com.example.bootserver.identity.internal.domain.UserAccount;
import org.springframework.stereotype.Repository;

/** 身份命令侧 MyBatis 适配器。 */
@Repository
public class MyBatisIdentityRepository implements IdentityRepository {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;

    public MyBatisIdentityRepository(UserMapper userMapper, RoleMapper roleMapper, UserRoleMapper userRoleMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    public boolean usernameExists(String username) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0;
    }

    @Override
    public boolean emailExists(String email) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0;
    }

    @Override
    public UserAccount insert(UserAccount account) {
        User entity = new User();
        entity.setUsername(account.username());
        entity.setName(account.name());
        entity.setEmail(account.email());
        entity.setPasswordHash(account.passwordHash());
        entity.setStatus(account.status());
        entity.setAge(account.age());
        userMapper.insert(entity);
        return toDomain(entity);
    }

    @Override
    public UserAccount findByUsername(String username) {
        User entity = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public UserAccount findById(Long id) {
        User entity = userMapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public UserAccount findByUsernameIncludingDeleted(String username) {
        User entity = userMapper.selectByUsernameIncludingDeleted(username);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public void bindRole(Long userId, String roleCode) {
        Role role = findRequiredRole(roleCode);
        UserRole binding = new UserRole();
        binding.setUserId(userId);
        binding.setRoleId(role.getId());
        userRoleMapper.insert(binding);
    }

    @Override
    public void bindRoleIfAbsent(Long userId, String roleCode) {
        Role role = findRequiredRole(roleCode);
        if (userRoleMapper.countByUserIdAndRoleId(userId, role.getId()) > 0) {
            return;
        }
        UserRole binding = new UserRole();
        binding.setUserId(userId);
        binding.setRoleId(role.getId());
        userRoleMapper.insert(binding);
    }

    @Override
    public boolean updateProfile(Long id, String name, String email, Integer age) {
        User update = new User();
        update.setId(id);
        update.setName(name);
        update.setEmail(email);
        update.setAge(age);
        return userMapper.updateById(update) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return userMapper.deleteById(id) > 0;
    }

    private Role findRequiredRole(String roleCode) {
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, roleCode));
        if (role == null) {
            throw new IllegalStateException(roleCode + " 角色不存在，请检查 schema.sql 初始数据");
        }
        return role;
    }

    private UserAccount toDomain(User entity) {
        return new UserAccount(entity.getId(), entity.getUsername(), entity.getName(), entity.getEmail(),
                entity.getPasswordHash(), entity.getStatus(), entity.getAge(), entity.getDeleted(),
                entity.getCreateTime(), entity.getUpdateTime());
    }
}
