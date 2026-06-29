package com.smart.kf.config;

import com.smart.kf.model.FileUpload;
import com.smart.kf.repository.FileUploadRepository;
import com.smart.kf.service.RbacService;
import com.smart.kf.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.Optional;

/**
 * 组织标签授权过滤器（重构版）
 * 职责简化为两类：
 *   1. 为需要用户ID但不需要资源权限检查的 API，从 JWT 中提取并注入 userId/username/role 请求属性
 *   2. 对包含资源ID的请求，委托 RbacService 进行统一的资源级权限判断
 * 权限判断逻辑已全部迁移到 RbacService，不再在本过滤器中直接查询数据库或手动比对 org_tag。
 */
@Component
public class OrgTagAuthorizationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(OrgTagAuthorizationFilter.class);

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private FileUploadRepository fileUploadRepository;

    @Autowired
    private RbacService rbacService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // ===== 第一类：仅需注入 userId 的 API，不做资源权限校验 =====
        if (isUserIdOnlyPath(path, request)) {
            try {
                injectUserAttributesOnly(request);
            } catch (Exception e) {
                logger.warn("userId 注入失败，忽略: {}", e.getMessage());
            }
            filterChain.doFilter(request, response);
            return;
        }

        // ===== 提取资源ID + 权限校验（仅 filter 自身逻辑在 try-catch 内）=====
        boolean shouldProceed;
        try {
            shouldProceed = checkAuthorization(request, response);
        } catch (Exception e) {
            logger.error("资源授权过滤器错误: {}", e.getMessage(), e);
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
            return;
        }

        // filterChain.doFilter 在 try-catch 外，下游异常正常向上传播
        if (shouldProceed) {
            filterChain.doFilter(request, response);
        }
    }

    /**
     * 执行资源级权限校验，返回是否放行。
     * 此方法内可抛出 RuntimeException（过滤器自身错误），但不调用 filterChain.doFilter。
     */
    private boolean checkAuthorization(HttpServletRequest request, HttpServletResponse response) {
        String path = request.getRequestURI();

        String resourceId = extractResourceIdFromPath(request);
        if (resourceId == null) {
            logger.debug("未找到资源ID，直接放行: {}", path);
            return true;
        }

        ResourceInfo resourceInfo = getResourceInfo(resourceId);
        if (resourceInfo == null) {
            logger.debug("资源未找到: {}", resourceId);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return false;
        }

        if (resourceInfo.isPublic()) {
            logger.debug("公开资源，直接放行: {}", resourceId);
            return true;
        }

        String token = extractToken(request);
        if (token == null) {
            logger.debug("未找到 Token，返回 401");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        String username = jwtUtils.extractUsernameFromToken(token);
        String requiredPerm = httpMethodToPermission(request.getMethod());

        boolean allowed = rbacService.hasResourcePermission(
            username,
            "doc",
            resourceId,
            requiredPerm,
            resourceInfo.getOwner(),
            resourceInfo.getOrgTag(),
            resourceInfo.isPublic()
        );

        if (allowed) {
            String userId = jwtUtils.extractUserIdFromToken(token);
            String role = jwtUtils.extractRoleFromToken(token);
            if (userId != null) {
                request.setAttribute("userId", userId);
                request.setAttribute("username", username);
                request.setAttribute("role", role);
            }
        } else {
            logger.debug("用户 {} 无权访问资源 {} (requiredPerm={})", username, resourceId, requiredPerm);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        }
        return allowed;
    }

    /**
     * 判断请求路径是否只需注入用户ID（不需要资源权限校验）
     */
    private boolean isUserIdOnlyPath(String path, HttpServletRequest request) {
        return path.matches(".*/upload/chunk.*")
            || path.matches(".*/upload/merge.*")
            || path.matches(".*/documents/uploads.*")
            || path.matches(".*/search/hybrid.*")
            || (path.matches(".*/documents/[a-fA-F0-9]{32}.*") && "DELETE".equals(request.getMethod()));
    }

    /**
     * 从 JWT 中提取 userId/username/role 并设置为请求属性（不调用 filterChain.doFilter）
     */
    private void injectUserAttributesOnly(HttpServletRequest request) {
        String token = extractToken(request);
        if (token != null) {
            String userId = jwtUtils.extractUserIdFromToken(token);
            String username = jwtUtils.extractUsernameFromToken(token);
            String role = jwtUtils.extractRoleFromToken(token);
            if (userId != null) {
                request.setAttribute("userId", userId);
                request.setAttribute("username", username);
                request.setAttribute("role", role);
                logger.debug("注入用户属性: userId={}, username={}, role={}", userId, username, role);
            }
        }
    }

    /**
     * 将 HTTP 方法映射到权限操作
     */
    private String httpMethodToPermission(String method) {
        return switch (method.toUpperCase()) {
            case "GET"  -> "read";
            case "POST" -> "write";
            case "PUT", "PATCH" -> "write";
            case "DELETE" -> "delete";
            default -> "read";
        };
    }

    /**
     * 从路径中提取资源ID
     */
    private String extractResourceIdFromPath(HttpServletRequest request) {
        String path = request.getRequestURI();

        // 文件资源: /api/v1/files/{fileMd5}
        if (path.matches(".*/files/[^/]+.*")) {
            return path.replaceAll(".*/files/([^/]+).*", "$1");
        }
        // 文档删除资源: /api/v1/documents/{file_md5} (MD5格式)
        if (path.matches(".*/documents/[a-fA-F0-9]{32}.*")) {
            return path.replaceAll(".*/documents/([a-fA-F0-9]{32}).*", "$1");
        }
        // 文档资源: /api/v1/documents/{docId} (数字ID)
        if (path.matches(".*/documents/\\d+.*")) {
            return path.replaceAll(".*/documents/(\\d+).*", "$1");
        }
        // 上传分片: 从请求头取文件MD5
        if (path.matches(".*/upload/chunk.*")) {
            return request.getHeader("X-File-MD5");
        }
        return null;
    }

    /**
     * 查询资源基础信息（从 file_upload 表）
     */
    private ResourceInfo getResourceInfo(String resourceId) {
        if (resourceId == null) return null;
        Optional<FileUpload> fileUpload = fileUploadRepository.findByFileMd5(resourceId);
        return fileUpload.map(f -> new ResourceInfo(f.getUserId(), f.getOrgTag(), f.isPublic())).orElse(null);
    }

    /**
     * 从请求头提取 JWT Token
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        return (bearerToken != null && bearerToken.startsWith("Bearer "))
            ? bearerToken.substring(7) : null;
    }

    /**
     * 资源基础信息（内部封装类）
     */
    @Getter
    private static class ResourceInfo {
        private final String owner;
        private final String orgTag;
        private final boolean isPublic;

        public ResourceInfo(String owner, String orgTag, boolean isPublic) {
            this.owner = owner;
            this.orgTag = orgTag;
            this.isPublic = isPublic;
        }
    }
}
