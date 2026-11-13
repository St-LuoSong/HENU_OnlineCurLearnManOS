package com.onlinecurlearnmanos.security;

import com.onlinecurlearnmanos.admin.controller.AdminUserController;
import com.onlinecurlearnmanos.admin.service.AdminUserService;
import com.onlinecurlearnmanos.cloudclass.controller.CloudClassController;
import com.onlinecurlearnmanos.cloudclass.service.CloudClassService;
import com.onlinecurlearnmanos.common.web.RestAccessDeniedHandler;
import com.onlinecurlearnmanos.common.web.RestAuthenticationEntryPoint;
import com.onlinecurlearnmanos.config.SecurityConfig;
import com.onlinecurlearnmanos.content.controller.KnowledgePointController;
import com.onlinecurlearnmanos.content.service.KnowledgePointService;
import com.onlinecurlearnmanos.forum.controller.AdminForumController;
import com.onlinecurlearnmanos.forum.service.ForumAnnouncementService;
import com.onlinecurlearnmanos.forum.service.ForumGovernanceService;
import com.onlinecurlearnmanos.user.controller.AdminPasswordRequestController;
import com.onlinecurlearnmanos.user.service.PasswordChangeService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 权限矩阵回归：真实加载 Spring Security 过滤链与 {@code @EnableMethodSecurity}，
 * 按「学生 / 教师 / 管理员 / 未登录」四种身份验证关键端点的可达性。
 */
@WebMvcTest(controllers = {
        AdminUserController.class,
        AdminForumController.class,
        AdminPasswordRequestController.class,
        KnowledgePointController.class,
        CloudClassController.class
})
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class RolePermissionWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private TokenRevocationService tokenRevocationService;

    @MockitoBean
    private AdminUserService adminUserService;

    @MockitoBean
    private KnowledgePointService knowledgePointService;

    @MockitoBean
    private CloudClassService cloudClassService;

    @MockitoBean
    private ForumGovernanceService forumGovernanceService;

    @MockitoBean
    private ForumAnnouncementService forumAnnouncementService;

    @MockitoBean
    private PasswordChangeService passwordChangeService;

    @Test
    void adminEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointRejectsStudentsAndTeachers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").with(asRole("2024001", "STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/users").with(asRole("T2026001", "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpointAllowsAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").with(asRole("admin", "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void userDeletionIsAdminOnly() throws Exception {
        String body = "{\"reason\":\"违规账号\",\"confirmLoginId\":\"2024001\"}";

        mockMvc.perform(delete("/api/v1/admin/users/7")
                        .with(asRole("2024001", "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/users/7")
                        .with(asRole("T2026001", "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/users/7")
                        .with(asRole("admin", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void forumGovernanceIsAdminOnly() throws Exception {
        mockMvc.perform(get("/api/v1/admin/forum/reports").with(asRole("2024001", "STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/forum/reports").with(asRole("T2026001", "TEACHER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/forum/reports").with(asRole("admin", "ADMIN")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/forum/moderate")
                        .with(asRole("T2026001", "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":1,\"action\":\"HIDE\",\"note\":\"违规\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void announcementGovernanceIsAdminOnly() throws Exception {
        mockMvc.perform(put("/api/v1/admin/forum/announcements/7/status")
                        .param("status", "HIDDEN")
                        .with(asRole("2024001", "STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/forum/announcements/7/status")
                        .param("status", "HIDDEN")
                        .with(asRole("T2026001", "TEACHER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/forum/announcements")
                        .param("status", "HIDDEN")
                        .with(asRole("2024001", "STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/forum/announcements")
                        .param("status", "HIDDEN")
                        .with(asRole("admin", "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void knowledgePointMaintenanceIsTeacherOrAdminOnly() throws Exception {
        mockMvc.perform(delete("/api/v1/knowledge-points/9").with(asRole("2024001", "STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/knowledge-points/9").with(asRole("T2026001", "TEACHER")))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/knowledge-points/9").with(asRole("admin", "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void passwordChangeApprovalIsAdminOnly() throws Exception {
        mockMvc.perform(get("/api/v1/admin/password-requests").with(asRole("2024001", "STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/password-requests").with(asRole("T2026001", "TEACHER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/password-requests").with(asRole("admin", "ADMIN")))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/admin/password-requests/7/review")
                        .with(asRole("T2026001", "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"note\":\"同意\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cloudClassCreationIsTeacherOnly() throws Exception {
        mockMvc.perform(post("/api/v1/cloud-classes")
                        .with(asRole("2024001", "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":1,\"name\":\"软件工程 2026 春\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/cloud-classes")
                        .with(asRole("T2026001", "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":1,\"name\":\"软件工程 2026 春\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void cloudClassSubmissionIsStudentOnly() throws Exception {
        mockMvc.perform(submitAs("T2026001", "TEACHER"))
                .andExpect(status().isForbidden());
        mockMvc.perform(submitAs("admin", "ADMIN"))
                .andExpect(status().isForbidden());
        mockMvc.perform(submitAs("2024001", "STUDENT"))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder submitAs(
            String username,
            String role
    ) {
        return post("/api/v1/cloud-classes/tasks/5/submit")
                .with(asRole(username, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"我的作业答案\"}");
    }

    private RequestPostProcessor asRole(String username, String role) {
        UserPrincipal principal =
                new UserPrincipal(31L, username, "n/a", username, null, 1, List.of(role));
        return authentication(new UsernamePasswordAuthenticationToken(
                principal,
                "n/a",
                principal.getAuthorities()
        ));
    }

    /**
     * 切片测试不加载 MyBatis 自动配置，但应用类带有 {@code @MapperScan}，
     * 这里提供一个不连数据库的 SqlSessionFactory，让 MapperFactoryBean 能完成初始化。
     * 本测试不会真正执行任何 SQL。
     */
    @TestConfiguration
    static class MapperSliceSupport {

        @Bean
        SqlSessionFactory sqlSessionFactory() {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setEnvironment(new Environment(
                    "slice-test",
                    new JdbcTransactionFactory(),
                    new UnpooledDataSource()
            ));
            return new MybatisSqlSessionFactoryBuilder().build(configuration);
        }
    }
}
