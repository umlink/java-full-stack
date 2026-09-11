package com.example.bootserver.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册接口允许客户端提交的字段白名单。
 * <p>
 * 只有用户名、邮箱、密码三个注册要素，对应“请求体不能指定角色或 ADMIN”的约束：
 * 即使 JSON 里塞入 {@code role} / {@code roles}，Jackson 也会因 {@code ignoreUnknown = true}
 * 把它们丢弃，DTO 上根本不提供写入角色或权限的字段，从类型层面拒绝越权指定 ADMIN。
 * <p>
 * 密码只校验“必须填写”，复杂度策略属于本卡“不做”范围，留给后续需求而非这里顺手实现。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RegisterRequest(
        // 与 t_user.username VARCHAR(64) 对齐，先在 Web 层拒绝超长值，避免数据库异常伪装成 500。
        @NotBlank(message = "用户名不能为空") @Size(max = 64, message = "用户名不能超过 64 个字符") String username,
        // 邮箱既用于登录找回等场景，也是账号唯一业务键之一，重复将触发 409 冲突
        @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱不能超过 128 个字符") String email,
        // 仅约束“非空”，具体强度策略不在当前卡片范围
        @NotBlank(message = "密码不能为空") String password
) {
}
