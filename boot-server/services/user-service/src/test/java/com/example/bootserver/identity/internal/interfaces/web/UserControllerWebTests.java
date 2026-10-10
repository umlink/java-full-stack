package com.example.bootserver.identity.internal.interfaces.web;

import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.common.result.Result;
import com.example.bootserver.identity.internal.application.command.CreateUserCommand;
import com.example.bootserver.identity.internal.application.command.UpdateUserCommand;
import com.example.bootserver.identity.internal.application.query.UserQueryService;
import com.example.bootserver.identity.internal.application.query.UserView;
import com.example.bootserver.common.error.BusinessException;
import com.example.bootserver.handler.GlobalExceptionHandler;
import com.example.bootserver.identity.internal.application.command.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerWebTests {

    private final UserService userService = org.mockito.Mockito.mock(UserService.class);
    private final UserQueryService userQueryService = org.mockito.Mockito.mock(UserQueryService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // 独立装配接口层，聚焦 JSON 绑定和参数校验；显式注册全局异常处理器以验证最终 API 响应。
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService, userQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createUsesDtoAllowlistAndIgnoresClientControlledFields() throws Exception {
        when(userService.createUser(any(CreateUserCommand.class))).thenAnswer(invocation -> {
            CreateUserCommand command = invocation.getArgument(0);
            assertThat(command.name()).isEqualTo("Dave");
            assertThat(command.email()).isEqualTo("dave@example.com");
            assertThat(command.age()).isEqualTo(32);
            return 100L;
        });

        // standaloneSetup 不加载 application.yml，因此这里验证 Controller 自己声明的内部资源路径。
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":999,"deleted":1,"name":"Dave","email":"dave@example.com","age":32}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS_CODE))
                .andExpect(jsonPath("$.data").value(100));
    }

    @Test
    void createRejectsBlankNameBeforeCallingService() throws Exception {
        assertInvalidCreateRequest(
                """
                        {"name":"   ","email":"dave@example.com","age":32}
                        """,
                "姓名不能为空");
    }

    @Test
    void createRejectsMalformedEmailBeforeCallingService() throws Exception {
        assertInvalidCreateRequest(
                """
                        {"name":"Dave","email":"not-an-email","age":32}
                        """,
                "邮箱格式不正确");
    }

    @Test
    void createRejectsOutOfRangeAgeBeforeCallingService() throws Exception {
        assertInvalidCreateRequest(
                """
                        {"name":"Dave","email":"dave@example.com","age":151}
                        """,
                "年龄不能大于 150");
    }

    @Test
    void getReturnsUserWhenTargetExists() throws Exception {
        UserView user = new UserView(1L, "alice", "Alice", "alice@example.com",
                1, 20, 0, null, null);
        when(userQueryService.getRequiredById(1L)).thenReturn(user);

        mockMvc.perform(get("/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS_CODE))
                .andExpect(jsonPath("$.data.id").value(1))
                // 回归保护：User 会直接作为查询响应 data，密码哈希必须在序列化层被排除。
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        verify(userQueryService).getRequiredById(1L);
    }

    @Test
    void getReturnsNotFoundWhenTargetIsMissing() throws Exception {
        when(userQueryService.getRequiredById(404L))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));

        mockMvc.perform(get("/users/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("用户不存在"));

        verify(userQueryService).getRequiredById(404L);
    }

    @Test
    void updateUsesDtoAllowlistAndPathId() throws Exception {
        when(userService.updateUserProfile(org.mockito.ArgumentMatchers.eq(1L), any(UpdateUserCommand.class)))
                .thenAnswer(invocation -> {
                    UpdateUserCommand command = invocation.getArgument(1);
                    assertThat(command.age()).isEqualTo(26);
                    assertThat(command.name()).isNull();
                    assertThat(command.email()).isNull();
                    return true;
                });

        mockMvc.perform(put("/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":999,"status":0,"deleted":1,"age":26}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS_CODE))
                .andExpect(jsonPath("$.data").value(true));

        verify(userService).updateUserProfile(org.mockito.ArgumentMatchers.eq(1L), any(UpdateUserCommand.class));
    }

    @Test
    void updateReturnsNotFoundWithoutExecutingUpdateWhenTargetIsMissing() throws Exception {
        when(userService.updateUserProfile(org.mockito.ArgumentMatchers.eq(404L), any(UpdateUserCommand.class)))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));

        mockMvc.perform(put("/users/404")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"age":26}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("用户不存在"));

        verify(userService).updateUserProfile(org.mockito.ArgumentMatchers.eq(404L), any(UpdateUserCommand.class));
    }

    @Test
    void updateRejectsRequestWithoutAllowedFieldsBeforeCallingService() throws Exception {
        mockMvc.perform(put("/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0,\"deleted\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAMETER_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value("至少提供一个可更新字段"));

        verifyNoInteractions(userService, userQueryService);
    }

    @Test
    void pageRejectsOutOfRangeParametersBeforeCallingService() throws Exception {
        mockMvc.perform(get("/users/page")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAMETER_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value("页码必须大于 0"));

        verifyNoInteractions(userService, userQueryService);
    }

    @Test
    void pageRejectsSizeAboveMaximumBeforeCallingService() throws Exception {
        mockMvc.perform(get("/users/page")
                .param("page", "1")
                .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAMETER_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value("每页数量不能超过 100"));

        verifyNoInteractions(userService, userQueryService);
    }

    private void assertInvalidCreateRequest(String requestBody, String expectedMessage) throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAMETER_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value(expectedMessage));

        // 验证顺序：校验失败在 Controller 方法执行前中断，因此持久化服务绝不能被调用。
        verifyNoInteractions(userService, userQueryService);
    }

}
