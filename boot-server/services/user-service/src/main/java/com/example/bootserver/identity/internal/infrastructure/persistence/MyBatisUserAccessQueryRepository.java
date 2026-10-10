package com.example.bootserver.identity.internal.infrastructure.persistence;

import com.example.bootserver.identity.internal.application.query.UserAccessQueryRepository;
import com.example.bootserver.identity.internal.domain.UserAccount;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 请求认证查询侧 MyBatis 适配器。 */
@Repository
public class MyBatisUserAccessQueryRepository implements UserAccessQueryRepository {

    private final UserAuthorityMapper mapper;

    public MyBatisUserAccessQueryRepository(UserAuthorityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean isActiveUser(Long userId) {
        return mapper.selectActiveUserIdById(userId, UserAccount.STATUS_ACTIVE, UserAccount.NOT_DELETED) != null;
    }

    @Override
    public List<String> findPermissionCodes(Long userId) {
        return mapper.selectPermissionCodesByUserId(
                userId, UserAccount.STATUS_ACTIVE, UserAccount.NOT_DELETED);
    }
}
