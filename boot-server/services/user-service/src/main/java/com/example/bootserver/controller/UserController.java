package com.example.bootserver.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.common.result.Result;
import com.example.bootserver.entity.User;
import com.example.bootserver.service.UserService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户接口 —— REST CRUD 演示。
 * <p>
 * 设计对齐：RESTful 资源风格（阶段二 03 讲）、统一响应体 Result（03 讲）、构造器注入（阶段一 01 讲）。
 * 逻辑删除、分页均由 MyBatis-Plus 全局配置接管，接口层只声明意图。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 列表：GET /api/users */
    @GetMapping
    public Result<List<User>> list() {
        return Result.ok(userService.list());
    }

    /** 分页：GET /api/users/page?page=1&size=10 */
    @GetMapping("/page")
    public Result<Page<User>> page(@RequestParam(defaultValue = "1") long page,
                                   @RequestParam(defaultValue = "10") long size) {
        return Result.ok(userService.page(new Page<>(page, size)));
    }

    /** 按名称模糊查询（LambdaQueryWrapper 写法示例）：GET /api/users/by-name?name=xxx */
    @GetMapping("/by-name")
    public Result<List<User>> byName(@RequestParam String name) {
        List<User> users = userService.list(new LambdaQueryWrapper<User>()
                .like(User::getName, name));
        return Result.ok(users);
    }

    /** 详情：GET /api/users/{id} */
    @GetMapping("/{id}")
    public Result<User> get(@PathVariable Long id) {
        User user = userService.getById(id);
        // 具体文案可以描述用户资源，但业务码统一从 ErrorCode 读取，避免 Controller 散落魔法数字。
        return user == null ? Result.fail(ErrorCode.NOT_FOUND, "用户不存在") : Result.ok(user);
    }

    /** 新增：POST /api/users（body 传 JSON，如 {"name":"Dave","email":"dave@example.com","age":32}） */
    @PostMapping
    public Result<Long> create(@RequestBody User user) {
        userService.save(user);
        return Result.ok(user.getId());
    }

    /** 更新：PUT /api/users/{id} */
    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        return Result.ok(userService.updateById(user));
    }

    /** 删除（逻辑删除）：DELETE /api/users/{id} —— 实际执行 UPDATE t_user SET deleted=1 */
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.ok(userService.removeById(id));
    }
}
