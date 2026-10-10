package com.example.bootserver.identity.internal.application.query;

import com.example.bootserver.common.error.BusinessException;
import com.example.bootserver.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 用户管理读模型服务。 */
@Service
@Transactional(readOnly = true)
public class UserQueryService {

    private final UserQueryRepository repository;

    public UserQueryService(UserQueryRepository repository) {
        this.repository = repository;
    }

    public UserView getRequiredById(Long id) {
        UserView user = repository.findById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    public List<UserView> listUsers() {
        return repository.findAll();
    }

    public UserPage listUsersByPage(long page, long size) {
        return repository.findPage(page, size);
    }

    public List<UserView> searchUsersByName(String name) {
        return repository.findByName(name);
    }
}
