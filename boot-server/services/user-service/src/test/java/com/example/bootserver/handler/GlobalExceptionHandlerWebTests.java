package com.example.bootserver.handler;

import com.example.bootserver.common.error.BusinessException;
import com.example.bootserver.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerWebTests {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // 独立装配 Controller 与 Advice，专注验证 Web 契约，不依赖数据库或完整应用上下文。
        mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void businessExceptionUsesItsErrorContract() throws Exception {
        // 防止未来把业务冲突错误地降级为 HTTP 200 或通用 500。
        mockMvc.perform(get("/test/errors/business"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT.getCode()))
                .andExpect(jsonPath("$.message").value("邮箱已注册"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void unexpectedExceptionDoesNotLeakInternalDetails() throws Exception {
        // 防止内部异常消息经 JSON 返回，避免暴露实现细节给调用方。
        mockMvc.perform(get("/test/errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value("系统繁忙，请稍后再试"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal secret"))))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @RestController
    @RequestMapping("/test/errors")
    static class FailingController {

        @GetMapping("/business")
        void business() {
            throw new BusinessException(ErrorCode.CONFLICT, "邮箱已注册");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("internal secret");
        }
    }
}
