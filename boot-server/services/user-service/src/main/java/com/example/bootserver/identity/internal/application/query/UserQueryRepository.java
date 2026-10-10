package com.example.bootserver.identity.internal.application.query;

import java.util.List;

/** 用户管理查询侧端口。 */
public interface UserQueryRepository {

    UserView findById(Long id);

    List<UserView> findAll();

    UserPage findPage(long page, long size);

    List<UserView> findByName(String name);
}
