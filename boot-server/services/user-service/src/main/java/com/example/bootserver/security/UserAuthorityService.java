package com.example.bootserver.security;

import com.example.bootserver.mapper.UserAuthorityMapper;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 将数据库中的 RBAC 关系转换为 Spring Security 可识别的 authority。
 *
 * 每次请求在 JWT 身份校验后读取当前关系，管理员撤权或调权后不必等旧 JWT 过期才生效。
 */
@Service
public class UserAuthorityService {

    /** 用户管理权限码：安全路由、数据库种子和测试共同遵循的稳定契约。 */
    public static final String USER_MANAGEMENT_PERMISSION = "user:manage";

    private final UserAuthorityMapper userAuthorityMapper;

    public UserAuthorityService(UserAuthorityMapper userAuthorityMapper) {
        this.userAuthorityMapper = userAuthorityMapper;
    }

    public List<GrantedAuthority> loadAuthorities(Long userId) {
        return userAuthorityMapper.selectPermissionCodesByUserId(userId).stream()
                // Security 只判断字符串 authority 是否匹配；权限码来自数据库关系而非 JWT，避免令牌内权限过期。
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }
}
