package com.example.bootserver.identity.internal.application.query;

import java.util.List;

/** 请求认证所需的最小身份与权限查询端口。 */
public interface UserAccessQueryRepository {

    boolean isActiveUser(Long userId);

    List<String> findPermissionCodes(Long userId);
}
