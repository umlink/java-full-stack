package com.example.bootserver.controller;

import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.entity.User;
import com.example.bootserver.exception.UserNotFoundException;
import com.example.bootserver.handler.GlobalExceptionHandler;
import com.example.bootserver.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // 独立装配接口层，聚焦 JSON 绑定和参数校验；显式注册全局异常处理器以验证最终 API 响应。
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createUsesDtoAllowlistAndIgnoresClientControlledFields() throws Exception {
        when(userService.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);

            // 这两个断言保护 DTO 的安全边界：客户端 JSON 不能指定主键或逻辑删除状态。
            assertThat(user.getId()).isNull();
            assertThat(user.getDeleted()).isNull();
            assertThat(user.getName()).isEqualTo("Dave");
            assertThat(user.getEmail()).isEqualTo("dave@example.com");
            assertThat(user.getAge()).isEqualTo(32);

            // 模拟数据库自增主键，验证合法字段仍能完成创建并返回新 ID。
            user.setId(100L);
            return true;
        });

        // standaloneSetup 不加载 application.yml，因此这里验证 Controller 自己声明的资源路径 /users。
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":999,"deleted":1,"name":"Dave","email":"dave@example.com","age":32}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
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
        User user = new User();
        user.setId(1L);
        user.setName("Alice");
        user.setPasswordHash("$2a$10$never-return-this-hash-to-client");
        when(userService.getRequiredById(1L)).thenReturn(user);

        mockMvc.perform(get("/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1))
                // 回归保护：User 会直接作为查询响应 data，密码哈希必须在序列化层被排除。
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        verify(userService).getRequiredById(1L);
    }

    @Test
    void getReturnsNotFoundWhenTargetIsMissing() throws Exception {
        when(userService.getRequiredById(404L)).thenThrow(new UserNotFoundException());

        mockMvc.perform(get("/users/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("用户不存在"));

        verify(userService).getRequiredById(404L);
    }

    @Test
    void updateUsesPathIdAfterConfirmingTargetExists() throws Exception {
        User existingUser = new User();
        existingUser.setId(1L);
        when(userService.getRequiredById(1L)).thenReturn(existingUser);
        when(userService.updateById(any(User.class))).thenAnswer(invocation -> {
            User update = invocation.getArgument(0);
            assertThat(update.getId()).isEqualTo(1L);
            assertThat(update.getAge()).isEqualTo(26);
            return true;
        });

        mockMvc.perform(put("/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":999,"age":26}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        verify(userService).getRequiredById(1L);
        verify(userService).updateById(any(User.class));
    }

    @Test
    void updateReturnsNotFoundWithoutExecutingUpdateWhenTargetIsMissing() throws Exception {
        when(userService.getRequiredById(404L)).thenThrow(new UserNotFoundException());

        mockMvc.perform(put("/users/404")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"age":26}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("用户不存在"));

        verify(userService).getRequiredById(404L);
        verify(userService, never()).updateById(any(User.class));
    }

    private void assertInvalidCreateRequest(String requestBody, String expectedMessage) throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAMETER_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value(expectedMessage));

        // 验证顺序：校验失败在 Controller 方法执行前中断，因此持久化服务绝不能被调用。
        verifyNoInteractions(userService);
    }
}
