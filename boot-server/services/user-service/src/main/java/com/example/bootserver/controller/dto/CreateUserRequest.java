package com.example.bootserver.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建用户接口允许客户端提交的字段白名单。
 * <p>
 * DTO 描述的是“创建”这个动作，而不是数据库中的完整用户行。因此它不包含 {@code id}、
 * {@code deleted}、创建时间和更新时间等只能由服务端或数据库控制的字段。使用 record 表示
 * 请求数据在完成 JSON 绑定后不应被修改。
 * <p>
 * {@code ignoreUnknown = true} 让客户端额外传入的字段不会绑定到实体；即使请求中出现
 * {@code id} 或 {@code deleted}，它们也不能绕过 Controller 的字段白名单。
 * <p>
 * Controller 参数上的 {@code @Valid} 会在业务方法执行前读取本 DTO 的约束；任一约束不满足时，
 * Spring MVC 抛出 {@code MethodArgumentNotValidException}，全局异常处理器再统一转换为参数错误响应。
 * 这样 Service 不会接收到不完整或格式错误的创建数据。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateUserRequest(
        // 名称是数据库 NOT NULL 字段；空白字符串虽然能反序列化，却没有业务含义，因此必须拒绝。
        @NotBlank(message = "姓名不能为空") @Size(max = 64, message = "姓名不能超过 64 个字符") String name,
        // 先要求非空再校验邮箱格式，使调用方能分别得到“未填写”和“格式错误”的明确反馈。
        @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱不能超过 128 个字符") String email,
        // 年龄可不填；一旦提供则限制在合理范围内，避免脏数据进入持久层。
        @Min(value = 0, message = "年龄不能小于 0") @Max(value = 150, message = "年龄不能大于 150") Integer age
) {
}
