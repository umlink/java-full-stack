package com.example.bootserver.identity.internal.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.bootserver.identity.internal.application.query.UserPage;
import com.example.bootserver.identity.internal.application.query.UserQueryRepository;
import com.example.bootserver.identity.internal.application.query.UserView;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 用户管理查询侧 MyBatis 适配器。 */
@Repository
public class MyBatisUserQueryRepository implements UserQueryRepository {

    private final UserMapper userMapper;

    public MyBatisUserQueryRepository(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserView findById(Long id) {
        User user = userMapper.selectById(id);
        return user == null ? null : toView(user);
    }

    @Override
    public List<UserView> findAll() {
        return userMapper.selectList(new LambdaQueryWrapper<User>().orderByAsc(User::getId))
                .stream().map(this::toView).toList();
    }

    @Override
    public UserPage findPage(long page, long size) {
        Page<User> result = userMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<User>().orderByAsc(User::getId));
        return new UserPage(result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords().stream().map(this::toView).toList());
    }

    @Override
    public List<UserView> findByName(String name) {
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                        .like(User::getName, name).orderByAsc(User::getId))
                .stream().map(this::toView).toList();
    }

    private UserView toView(User user) {
        return new UserView(user.getId(), user.getUsername(), user.getName(), user.getEmail(),
                user.getStatus(), user.getAge(), user.getDeleted(), user.getCreateTime(), user.getUpdateTime());
    }
}
