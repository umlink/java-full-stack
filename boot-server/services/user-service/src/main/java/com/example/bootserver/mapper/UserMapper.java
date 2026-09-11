package com.example.bootserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.bootserver.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * User 的 Mapper —— 继承 {@link BaseMapper} 即获得单表 CRUD（无需手写 SQL）。
 * <p>
 * 这是一个接口不是类：MyBatis 在启动时为其生成代理实现（阶段二 01 讲「框架魔法」的实例），
 * 复杂查询再在这里声明自定义 SQL 方法。
 */
public interface UserMapper extends BaseMapper<User> {

    /**
     * 本地管理员引导需要识别同名的逻辑删除账号。
     *
     * MyBatis-Plus 的逻辑删除会自动过滤 deleted=1；这里使用显式 SQL 查询全部状态，
     * 才能在启动阶段给出配置错误，而不是误判账号不存在后触发数据库唯一键异常。
     */
    @Select("""
            SELECT id, username, name, email, password_hash, status, age, deleted, create_time, update_time
            FROM t_user
            WHERE username = #{username}
            """)
    User selectByUsernameIncludingDeleted(@Param("username") String username);
}
