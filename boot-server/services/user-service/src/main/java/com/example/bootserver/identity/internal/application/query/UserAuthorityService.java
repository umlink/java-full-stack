package com.example.bootserver.identity.internal.application.query;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 将数据库中的 RBAC 关系转换为 Spring Security 可识别的 authority。
 *
 * 每次请求在 JWT 身份校验后读取当前关系，管理员撤权或调权后不必等旧 JWT 过期才生效。
 */
@Service
public class UserAuthorityService {

    private final UserAccessQueryRepository repository;

    public UserAuthorityService(UserAccessQueryRepository repository) {
        this.repository = repository;
    }

    /**
     * 判断 JWT 主体是否仍可作为当前请求的认证身份。
     *
     * 权限为空的普通用户仍是合法身份，不能用 authority 列表是否为空替代此校验；
     * 必须直接确认用户行仍存在、未删除且状态正常。
     */
    public boolean isActiveUser(Long userId) {
        return repository.isActiveUser(userId);
    }

    public List<String> loadPermissionCodes(Long userId) {
        return repository.findPermissionCodes(userId);
    }
}
