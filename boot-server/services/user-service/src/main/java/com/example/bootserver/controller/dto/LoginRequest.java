package com.example.bootserver.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求的最小白名单：只允许提交用户名和密码。
 *
 * JSON 中即使附带角色、用户 ID 或 token 也不会绑定到该 DTO，认证身份只能由服务端查询的用户记录决定。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LoginRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password
) {
}
