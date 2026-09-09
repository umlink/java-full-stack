package com.example.bootserver.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 管理员更新用户基础资料时允许修改的字段白名单。
 *
 * id、账号状态、逻辑删除标记和审计字段均由服务端流程维护；未知字段即使出现在 JSON 中也不会绑定，
 * 因而不能借更新资料接口越权改变账号状态。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateUserRequest(
        @Pattern(regexp = ".*\\S.*", message = "姓名不能为空")
        @Size(max = 64, message = "姓名不能超过 64 个字符") String name,
        @Pattern(regexp = ".*\\S.*", message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱不能超过 128 个字符") String email,
        @Min(value = 0, message = "年龄不能小于 0")
        @Max(value = 150, message = "年龄不能大于 150") Integer age
) {

    /** PATCH 式更新至少应提供一个允许修改的字段，避免空请求看似成功却没有任何效果。 */
    @AssertTrue(message = "至少提供一个可更新字段")
    public boolean hasUpdateField() {
        return name != null || email != null || age != null;
    }
}
