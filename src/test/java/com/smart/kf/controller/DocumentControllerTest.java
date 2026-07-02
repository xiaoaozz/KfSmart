package com.smart.kf.controller;

import com.smart.kf.config.JwtAuthenticationFilter;
import com.smart.kf.config.OrgTagAuthorizationFilter;
import com.smart.kf.config.SecurityConfig;
import com.smart.kf.model.FileUpload;
import com.smart.kf.repository.FileUploadRepository;
import com.smart.kf.repository.KnowledgeBaseRepository;
import com.smart.kf.repository.OrganizationTagRepository;
import com.smart.kf.service.DocumentService;
import com.smart.kf.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DocumentController 集成测试。DocumentController 无类级 @PreAuthorize，
 * 鉴权依赖 SecurityConfig 的 anyRequest().authenticated() + OrgTagAuthorizationFilter
 * 注入的 request attributes（userId/username/role），此处用 requestAttr 直接模拟过滤器行为。
 */
@WebMvcTest(DocumentController.class)
@Import(SecurityConfig.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentService documentService;
    @MockBean
    private FileUploadRepository fileUploadRepository;
    @MockBean
    private OrganizationTagRepository organizationTagRepository;
    @MockBean
    private JwtUtils jwtUtils;
    @MockBean
    private KnowledgeBaseRepository knowledgeBaseRepository;

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
    }

    @Test
    @WithMockUser
    void deleteDocument_success_returns200() throws Exception {
        mockMvc.perform(delete("/api/v1/documents/{fileMd5}", "abc123")
                        .requestAttr("userId", "42")
                        .requestAttr("username", "alice")
                        .requestAttr("role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @WithMockUser
    void deleteDocument_notFound_returns404() throws Exception {
        doThrow(new IllegalArgumentException("文档不存在"))
                .when(documentService).deleteDocumentWithPermission(eq("missing"), eq("alice"));

        mockMvc.perform(delete("/api/v1/documents/{fileMd5}", "missing")
                        .requestAttr("userId", "42")
                        .requestAttr("username", "alice")
                        .requestAttr("role", "USER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @WithMockUser
    void deleteDocument_permissionDenied_returns403() throws Exception {
        doThrow(new SecurityException("权限不足"))
                .when(documentService).deleteDocumentWithPermission(eq("abc123"), eq("alice"));

        mockMvc.perform(delete("/api/v1/documents/{fileMd5}", "abc123")
                        .requestAttr("userId", "42")
                        .requestAttr("username", "alice")
                        .requestAttr("role", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @WithMockUser
    void getUserUploadedFiles_returnsMappedFileList() throws Exception {
        FileUpload file = new FileUpload();
        file.setId(1L);
        file.setFileMd5("abc123");
        file.setFileName("report.pdf");
        file.setTotalSize(1024L);
        file.setStatus(1);
        file.setUserId("42");
        when(documentService.getUserUploadedFiles("42", "alice")).thenReturn(List.of(file));

        mockMvc.perform(get("/api/v1/documents/uploads")
                        .requestAttr("userId", "42")
                        .requestAttr("username", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].fileName").value("report.pdf"))
                .andExpect(jsonPath("$.data.records[0].fileType").value("pdf"))
                .andExpect(jsonPath("$.data.records[0].status").value("done"));
    }

    @Test
    @WithMockUser
    void getUserUploadedFiles_withKeyword_usesKeywordSearch() throws Exception {
        FileUpload file = new FileUpload();
        file.setId(2L);
        file.setFileMd5("def456");
        file.setFileName("invoice.docx");
        file.setTotalSize(2048L);
        file.setStatus(0);
        file.setUserId("42");
        when(fileUploadRepository.findByUserIdAndFileNameContainingIgnoreCase("42", "invoice"))
                .thenReturn(List.of(file));

        mockMvc.perform(get("/api/v1/documents/uploads")
                        .requestAttr("userId", "42")
                        .requestAttr("username", "alice")
                        .param("keyword", "invoice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].fileName").value("invoice.docx"))
                .andExpect(jsonPath("$.data.records[0].status").value("pending"));
    }
}
