package com.example.bootserver.identity.internal.domain;

/** 身份命令侧端口；用户、角色与关联表的持久化细节留在基础设施层。 */
public interface IdentityRepository {

    boolean usernameExists(String username);

    boolean emailExists(String email);

    UserAccount insert(UserAccount account);

    UserAccount findByUsername(String username);

    UserAccount findById(Long id);

    UserAccount findByUsernameIncludingDeleted(String username);

    void bindRole(Long userId, String roleCode);

    void bindRoleIfAbsent(Long userId, String roleCode);

    boolean updateProfile(Long id, String name, String email, Integer age);

    boolean deleteById(Long id);
}
