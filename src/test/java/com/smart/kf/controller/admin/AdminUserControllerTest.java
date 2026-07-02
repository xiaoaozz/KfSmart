package com.smart.kf.controller.admin;

import com.smart.kf.config.JwtAuthenticationFilter;
import com.smart.kf.config.OrgTagAuthorizationFilter;
import com.smart.kf.config.SecurityConfig;
import com.smart.kf.model.User;
import com.smart.kf.repository.ConversationRepository;
import com.smart.kf.repository.KnowledgeBaseRepository;
import com.smart.kf.repository.OrganizationTagRepository;
import com.smart.kf.repository.RoleRepository;
import com.smart.kf.repository.UserFavoriteRepository;
import com.smart.kf.repository.UserRepository;
import com.smart.kf.service.AdminAuthHelper;
import com.smart.kf.service.RbacService;
import com.smart.kf.service.UserService;
import com.smart.kf.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminUserController 集成测试（首个 WebMvcTest 示例，作为后续 Admin 子控制器测试的模板）。
 * 安全过滤器 {@link JwtAuthenticationFilter}/{@link OrgTagAuthorizationFilter} 被替换为透传 mock，
 * 鉴权改由 {@link WithMockUser} 提供，与 {@code @PreAuthorize("hasAuthority('system:admin')")} 对齐。
 */
@WebMvcTest(AdminUserController.class)
@Import(SecurityConfig.class)
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;
    @MockBean
    private UserService userService;
    @MockBean
    private RbacService rbacService;
    @MockBean
    private JwtUtils jwtUtils;
    @MockBean
    private KnowledgeBaseRepository knowledgeBaseRepository;
    @MockBean
    private UserFavoriteRepository userFavoriteRepository;
    @MockBean
    private ConversationRepository conversationRepository;
    @MockBean
    private OrganizationTagRepository organizationTagRepository;
    @MockBean
    private RoleRepository roleRepository;
    @MockBean
    private AdminAuthHelper adminAuthHelper;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean
    private OrgTagAuthorizationFilter orgTagAuthorizationFilter;

    @BeforeEach
    void passThroughSecurityFilters() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(orgTagAuthorizationFilter).doFilter(any(), any(), any());

        when(jwtUtils.extractUsernameFromToken(any())).thenReturn("admin");
    }

    @Test
    @WithMockUser(authorities = "system:admin")
    void getAllUsers_returnsUserList() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        when(userRepository.findAll()).thenReturn(List.of(user));

        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @WithMockUser(authorities = "system:admin")
    void updateUser_notFound_returns404() throws Exception {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/admin/users/99")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @WithMockUser(authorities = "system:admin")
    void updateUser_success_returnsUpdatedUser() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/v1/admin/users/1")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("bob"));
    }

    @Test
    @WithMockUser(authorities = "system:admin")
    void deleteUser_adminAccount_returns403() throws Exception {
        User targetAdmin = new User();
        targetAdmin.setId(2L);
        targetAdmin.setRole(User.Role.ADMIN);
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetAdmin));

        mockMvc.perform(delete("/api/v1/admin/users/2").header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @WithMockUser(authorities = "system:admin")
    void deleteUser_success_returns200() throws Exception {
        User target = new User();
        target.setId(3L);
        target.setRole(User.Role.USER);
        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");
        when(userRepository.findById(3L)).thenReturn(Optional.of(target));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userFavoriteRepository.findByUserOrderByUpdatedAtDesc(target)).thenReturn(List.of());
        when(conversationRepository.findByUserId(3L)).thenReturn(List.of());
        when(organizationTagRepository.findByCreatedBy(target)).thenReturn(List.of());
        when(knowledgeBaseRepository.findByCreatedBy(target)).thenReturn(List.of());

        mockMvc.perform(delete("/api/v1/admin/users/3").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
