package com.example.bootserver.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.bootserver.common.result.Result;
import com.example.bootserver.controller.dto.CreateUserRequest;
import com.example.bootserver.entity.User;
import com.example.bootserver.service.UserService;
import jakarta.validation.Valid;
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
 * 逻辑删除、分页均由 MyBatis-Plus 全局配置接管，接口层只声明意图。外部统一前缀由
 * {@code spring.mvc.servlet.path=/api} 配置，本类只维护资源路径，避免每个 Controller 重复写 /api。
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 列表：GET /api/users（/api 由 MVC 前缀配置提供） */
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
        return Result.ok(userService.getRequiredById(id));
    }

    /**
     * 新增：POST /api/users。
     * <p>
     * 接口只接收创建 DTO，再手动映射为实体。{@code @Valid} 会先触发 DTO 上的字段校验；
     * 校验失败时方法不会执行，异常由全局处理器转换为统一的参数错误响应。当前仅有三个字段，
     * 显式赋值比引入映射框架更直观，也能让读者看清哪些字段绝不能由客户端控制。
     */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody CreateUserRequest request) {
        // User 的 id、deleted 和审计字段不从请求复制，分别交给数据库、框架和后续业务流程维护。
        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setAge(request.age());

        // ServiceImpl 会委托 Mapper 执行 INSERT；IdType.AUTO 让数据库生成主键，JDBC 再把 generated key
        // 回填到同一个 user 对象。因此 save 成功后无需再次查询，user.getId() 就是新记录的数据库 ID。
        userService.save(user);
        return Result.ok(user.getId());
    }

    /**
     * 更新：PUT /api/users/{id}。
     * <p>
     * URL 中的 {@code id} 是本次要修改的目标资源；请求体只描述要修改的字段。例如请求
     * {@code PUT /api/users/1} 携带 {@code {"age":26}}，表示把 ID 为 1 的用户年龄改为 26。
     * 在当前 MyBatis-Plus 默认字段策略下，实体中为 {@code null} 的普通字段通常不会出现在
     * UPDATE 的 SET 子句中，因此该请求不会主动覆盖 name 和 email。
     * <p>
     * 这是 M0 保留的最小 CRUD 示例，仍直接接收 {@link User}。M1-01 只建立“创建”接口的
     * DTO 边界，不提前改动更新接口；后续应为更新建立专用 DTO、参数校验和字段权限规则，
     * 避免客户端尝试修改 {@code deleted} 等服务端字段。
     */
    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody User user) {
        // 先由 Service 确认目标资源存在；不存在会抛出业务异常并由全局处理器返回 404，而非成功响应中的 false。
        userService.getRequiredById(id);

        // 以路径 ID 为准，忽略请求体里可能携带的 id，防止调用方把更新目标悄悄换成另一条记录。
        user.setId(id);

        // updateById 使用实体主键生成 WHERE id = ?；资源存在性已在更新前确认，返回值保持原有的受影响行数语义。
        boolean updated = userService.updateById(user);
        return Result.ok(updated);
    }

    /** 删除（逻辑删除）：DELETE /api/users/{id} —— 实际执行 UPDATE t_user SET deleted=1 */
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.ok(userService.removeById(id));
    }
}
