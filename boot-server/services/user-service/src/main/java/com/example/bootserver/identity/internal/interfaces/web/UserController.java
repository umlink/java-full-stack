package com.example.bootserver.identity.internal.interfaces.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.bootserver.common.result.Result;
import com.example.bootserver.identity.internal.interfaces.web.dto.CreateUserRequest;
import com.example.bootserver.identity.internal.interfaces.web.dto.UpdateUserRequest;
import com.example.bootserver.identity.internal.interfaces.web.dto.UserPageRequest;
import com.example.bootserver.config.OpenApiConfig;
import com.example.bootserver.identity.internal.application.command.CreateUserCommand;
import com.example.bootserver.identity.internal.application.command.UpdateUserCommand;
import com.example.bootserver.identity.internal.application.command.UserService;
import com.example.bootserver.identity.internal.application.query.UserPage;
import com.example.bootserver.identity.internal.application.query.UserQueryService;
import com.example.bootserver.identity.internal.application.query.UserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
 * {@code spring.mvc.servlet.path} 配置，本类只维护资源路径，避免每个 Controller 复制部署前缀。
 */
@RestController
@RequestMapping("/users")
@Tag(name = "用户管理", description = "个人身份自查与需要 user:manage 权限的后台用户管理接口")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTHENTICATION)
public class UserController {

    private final UserService userService;
    private final UserQueryService userQueryService;

    public UserController(UserService userService, UserQueryService userQueryService) {
        this.userService = userService;
        this.userQueryService = userQueryService;
    }

    /**
     * 当前登录用户：GET /users/me（外部前缀由运行时配置决定）。
     *
     * {@code @AuthenticationPrincipal} 从 SecurityContext 取出过滤器写入的 principal；本项目此阶段保存的是用户 ID。
     * 该接口只验证“已认证可访问”，不要求后台管理权限；其余用户管理资源由 M1-08 的权限规则保护。
     */
    @GetMapping("/me")
    @Operation(summary = "获取当前登录用户 ID", description = "任意有效 JWT 均可访问，仅返回认证主体 ID。")
    public Result<Long> getCurrentUserId(@AuthenticationPrincipal Long userId) {
        return Result.ok(userId);
    }

    /** 列表：GET /users（外部前缀由运行时配置决定）。 */
    @GetMapping
    @Operation(summary = "查询用户列表", description = "需要 user:manage 权限，按用户 ID 升序返回。")
    public Result<List<UserView>> listUsers() {
        return Result.ok(userQueryService.listUsers());
    }

    /** 分页：GET /users/page?page=1&size=10（外部前缀由运行时配置决定）。 */
    @GetMapping("/page")
    @Operation(summary = "分页查询用户", description = "需要 user:manage 权限；页码从 1 开始，每页最多 100 条。")
    public Result<Page<UserView>> listUsersByPage(@Valid @ModelAttribute UserPageRequest request) {
        UserPage page = userQueryService.listUsersByPage(request.getPage(), request.getSize());
        Page<UserView> response = new Page<>(page.page(), page.size(), page.total());
        response.setRecords(page.records());
        return Result.ok(response);
    }

    /** 按名称模糊查询：GET /users/by-name?name=xxx（外部前缀由运行时配置决定）。 */
    @GetMapping("/by-name")
    @Operation(summary = "按名称搜索用户", description = "需要 user:manage 权限，按用户 ID 升序返回匹配项。")
    public Result<List<UserView>> searchUsersByName(@RequestParam String name) {
        return Result.ok(userQueryService.searchUsersByName(name));
    }

    /** 详情：GET /users/{id}（外部前缀由运行时配置决定）。 */
    @GetMapping("/{id}")
    @Operation(summary = "查询用户详情", description = "需要 user:manage 权限；用户不存在时返回 404/40400。")
    public Result<UserView> getUserById(@PathVariable Long id) {
        return Result.ok(userQueryService.getRequiredById(id));
    }

    /**
     * 新增：POST /users（外部前缀由运行时配置决定）。
     * <p>
     * 接口只接收创建 DTO，再映射为应用命令。{@code @Valid} 会先触发 DTO 上的字段校验；
     * 校验失败时方法不会执行，异常由全局处理器转换为统一的参数错误响应。当前仅有三个字段，
     * 显式赋值比引入映射框架更直观，也能让读者看清哪些字段绝不能由客户端控制。
     */
    @PostMapping
    @Operation(summary = "创建用户", description = "需要 user:manage 权限；仅接受创建 DTO 中定义的字段。")
    public Result<Long> createUser(@Valid @RequestBody CreateUserRequest request) {
        // id、deleted 和审计字段不进入应用命令，分别交给数据库、框架和后续业务流程维护。
        return Result.ok(userService.createUser(
                new CreateUserCommand(request.name(), request.email(), request.age())));
    }

    /**
     * 更新：PUT /users/{id}（外部前缀由运行时配置决定）。
     * <p>
     * URL 中的 {@code id} 是本次要修改的目标资源；请求体只描述要修改的字段。例如请求
     * {@code PUT /users/1} 携带 {@code {"age":26}}，表示把 ID 为 1 的用户年龄改为 26；外部前缀由配置决定。
     * 在当前 MyBatis-Plus 默认字段策略下，实体中为 {@code null} 的普通字段通常不会出现在
     * UPDATE 的 SET 子句中，因此该请求不会主动覆盖 name 和 email。
     * <p>
     * 请求体使用专用 DTO，只允许映射基础资料字段；账号状态、逻辑删除和审计字段均没有外部写入入口。
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新用户基础资料", description = "需要 user:manage 权限；仅允许更新姓名、邮箱和年龄。")
    public Result<Boolean> updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return Result.ok(userService.updateUserProfile(id,
                new UpdateUserCommand(request.name(), request.email(), request.age())));
    }

    /** 删除（逻辑删除）：DELETE /users/{id} —— 实际执行 UPDATE t_user SET deleted=1。 */
    @DeleteMapping("/{id}")
    @Operation(summary = "逻辑删除用户", description = "需要 user:manage 权限；用户不存在时返回 404/40400。")
    public Result<Boolean> deleteUser(@PathVariable Long id) {
        return Result.ok(userService.deleteUserById(id));
    }
}
