package com.smart.kf.controller;

import com.smart.kf.model.FileUpload;
import com.smart.kf.model.OrganizationTag;
import com.smart.kf.repository.FileUploadRepository;
import com.smart.kf.repository.KnowledgeBaseRepository;
import com.smart.kf.repository.OrganizationTagRepository;
import com.smart.kf.service.DocumentService;
import com.smart.kf.utils.LogUtils;
import com.smart.kf.utils.JwtUtils;
import com.smart.kf.utils.pagination.PageQuery;
import com.smart.kf.utils.pagination.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文档控制器类，处理文档相关操作请求
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    @Autowired
    private DocumentService documentService;
    
    @Autowired
    private FileUploadRepository fileUploadRepository;
    
    @Autowired
    private OrganizationTagRepository organizationTagRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    /**
     * 删除文档及其相关数据
     * 
     * @param fileMd5 文件MD5
     * @param userId 当前用户ID
     * @param role 用户角色
     * @return 删除结果
     */
    @DeleteMapping("/{fileMd5}")
    public ResponseEntity<?> deleteDocument(
            @PathVariable String fileMd5,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "username", required = false) String username,
            @RequestAttribute("role") String role) {
        
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("DELETE_DOCUMENT");
        try {
            LogUtils.logBusiness("DELETE_DOCUMENT", userId, "接收到删除文档请求: fileMd5=%s, role=%s", fileMd5, role);
            
            // 使用权限感知的删除方法（admin 可删除任意文档，普通用户只能删除自己的）
            // operatorUsername 必须是真实用户名（JWT sub 字段），而非数字 userId
            String operatorUsername = (username != null && !username.isEmpty()) ? username : userId;
            documentService.deleteDocumentWithPermission(fileMd5, operatorUsername);
            
            LogUtils.logFileOperation(userId, "DELETE", fileMd5, fileMd5, "SUCCESS");
            monitor.end("文档删除成功");
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "文档删除成功");
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            LogUtils.logUserOperation(userId, "DELETE_DOCUMENT", fileMd5, "FAILED_PERMISSION_DENIED");
            monitor.end("删除失败：权限不足");
            Map<String, Object> response = new HashMap<>();
            response.put("code", HttpStatus.FORBIDDEN.value());
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        } catch (IllegalArgumentException e) {
            LogUtils.logUserOperation(userId, "DELETE_DOCUMENT", fileMd5, "FAILED_NOT_FOUND");
            monitor.end("删除失败：文档不存在");
            Map<String, Object> response = new HashMap<>();
            response.put("code", HttpStatus.NOT_FOUND.value());
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (Exception e) {
            LogUtils.logBusinessError("DELETE_DOCUMENT", userId, "删除文档失败: fileMd5=%s", e, fileMd5);
            monitor.end("删除失败: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("message", "删除文档失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 获取用户可访问的所有文件列表
     * 
     * @param userId 当前用户ID
     * @param orgTags 用户所属组织标签
     * @return 可访问的文件列表
     */
    @GetMapping("/accessible")
    public ResponseEntity<?> getAccessibleFiles(
            @RequestAttribute("userId") String userId,
            @RequestAttribute("orgTags") String orgTags) {
        
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("GET_ACCESSIBLE_FILES");
        try {
            LogUtils.logBusiness("GET_ACCESSIBLE_FILES", userId, "接收到获取可访问文件请求: orgTags=%s", orgTags);
            
            List<FileUpload> files = documentService.getAccessibleFiles(userId, orgTags);
            
            LogUtils.logUserOperation(userId, "GET_ACCESSIBLE_FILES", "file_list", "SUCCESS");
            LogUtils.logBusiness("GET_ACCESSIBLE_FILES", userId, "成功获取可访问文件: fileCount=%d", files.size());
            monitor.end("获取可访问文件成功");
            
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "获取可访问文件列表成功");
            response.put("data", files);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_ACCESSIBLE_FILES", userId, "获取可访问文件失败", e);
            monitor.end("获取可访问文件失败: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("message", "获取可访问文件列表失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 获取用户上传的所有文件列表（支持关键词搜索、类型筛选、知识库筛选、tab 切换）
     *
     * @param userId    当前用户ID
     * @param keyword   文件名关键词搜索（模糊匹配）
     * @param fileType  文件类型筛选（扩展名，如 pdf/doc 等）
     * @param orgTag    组织标签筛选
     * @param timeRange 时间范围筛选
     * @param isPublic  公开状态筛选
     * @param kbId      所属知识库筛选
     * @param mine      仅返回当前用户自己的文件（true=仅自己，false/null=全部）
     * @param sort      排序方式（updatedAt=按更新时间倒序）
     * @return 文件列表
     */
    @GetMapping("/uploads")
    public ResponseEntity<?> getUserUploadedFiles(
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "username", required = false) String username,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String fileType,
            @RequestParam(required = false) String orgTag,
            @RequestParam(required = false) String timeRange,
            @RequestParam(required = false) Boolean isPublic,
            @RequestParam(required = false) String kbId,
            @RequestParam(required = false) Boolean mine,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String cursor) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("GET_USER_UPLOADED_FILES");
        try {
            LogUtils.logBusiness("GET_USER_UPLOADED_FILES", userId,
                "接收到获取用户上传文件请求: keyword=%s, fileType=%s, orgTag=%s, timeRange=%s, isPublic=%s, kbId=%s, mine=%s, sort=%s",
                keyword, fileType, orgTag, timeRange, isPublic, kbId, mine, sort);

            // 根据 keyword + kbId 决定从哪个方法取基础列表
            List<FileUpload> files;
            boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
            boolean hasKbId = kbId != null && !kbId.isEmpty();

            // operatorUsername 用于管理员判断（必须是用户名），userId 用于数据库查询
            String operatorUsername = (username != null && !username.isEmpty()) ? username : userId;

            if (hasKeyword && hasKbId) {
                files = fileUploadRepository.findByUserIdAndFileNameContainingIgnoreCaseAndKbId(userId, keyword.trim(), kbId);
            } else if (hasKeyword) {
                files = fileUploadRepository.findByUserIdAndFileNameContainingIgnoreCase(userId, keyword.trim());
            } else {
                files = documentService.getUserUploadedFiles(userId, operatorUsername);
            }

            // 应用其余筛选条件
            List<FileUpload> filteredFiles = files.stream().filter(file -> {
                // mine=true 时只展示当前用户自己的文件（默认已是，该分支保持语义清晰）
                if (Boolean.TRUE.equals(mine) && !userId.equals(file.getUserId())) {
                    return false;
                }
                // 知识库筛选（仅当未在 SQL 层面过滤时才在内存再过滤）
                if (!hasKeyword && hasKbId && !kbId.equals(file.getKbId())) {
                    return false;
                }
                // 文件类型筛选（根据文件扩展名）
                if (fileType != null && !fileType.isEmpty() && !"全部".equals(fileType) && !"全部类型".equals(fileType)) {
                    String fileName = file.getFileName();
                    if (fileName == null || !fileName.toLowerCase().endsWith("." + fileType.toLowerCase())) {
                        return false;
                    }
                }
                // 组织标签筛选
                if (orgTag != null && !orgTag.isEmpty() && !"全部".equals(orgTag)) {
                    if (!orgTag.equals(file.getOrgTag())) {
                        return false;
                    }
                }
                // 时间范围筛选
                if (timeRange != null && !timeRange.isEmpty() && !"全部时间".equals(timeRange)) {
                    LocalDateTime since = parseTimeRange(timeRange);
                    if (since != null && file.getCreatedAt() != null && file.getCreatedAt().isBefore(since)) {
                        return false;
                    }
                }
                // 公开状态筛选
                return isPublic == null || isPublic == file.isPublic();
            }).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));

            // 排序：sort=updatedAt 按 mergedAt/createdAt 倒序
            if ("updatedAt".equals(sort)) {
                filteredFiles.sort((a, b) -> {
                    java.time.LocalDateTime ta = a.getMergedAt() != null ? a.getMergedAt() : a.getCreatedAt();
                    java.time.LocalDateTime tb = b.getMergedAt() != null ? b.getMergedAt() : b.getCreatedAt();
                    if (ta == null && tb == null) return 0;
                    if (ta == null) return 1;
                    if (tb == null) return -1;
                    return tb.compareTo(ta); // 倒序
                });
            }

            // 将 FileUpload 转换为前端期望的 Document DTO 格式
            // 状态映射：0=pending, 1=done (processing 由解析任务队列驱动，前端通过轮询发现状态变化)
            List<Map<String, Object>> fileData = filteredFiles.stream().map(file -> {
                Map<String, Object> dto = new HashMap<>();
                dto.put("id", file.getId());
                dto.put("fileMd5", file.getFileMd5());
                dto.put("fileName", file.getFileName());
                // 提取文件扩展名作为 fileType
                dto.put("fileType", extractFileExtension(file.getFileName()));
                dto.put("fileSize", file.getTotalSize());
                // 状态映射：0=pending, 1=processing/done
                dto.put("status", mapFileStatus(file.getStatus()));
                // 从文件扩展名提取知识库名称（如果有 kbId）
                if (file.getKbId() != null && !file.getKbId().isEmpty()) {
                    dto.put("knowledgeBaseId", file.getKbId());
                    dto.put("knowledgeBaseName", getKnowledgeBaseName(file.getKbId()));
                }
                dto.put("uploadedBy", file.getUserId());
                dto.put("createTime", file.getCreatedAt());
                dto.put("updateTime", file.getMergedAt() != null ? file.getMergedAt() : file.getCreatedAt());
                // chunkCount 暂设为 0，前端可通过预览接口获取实际分片数
                dto.put("chunkCount", 0);
                return dto;
            }).toList();

            LogUtils.logUserOperation(userId, "GET_USER_UPLOADED_FILES", "file_list", "SUCCESS");
            LogUtils.logBusiness("GET_USER_UPLOADED_FILES", userId,
                "成功获取用户上传文件: totalCount=%d, filteredCount=%d, keyword=%s, kbId=%s",
                files.size(), fileData.size(), keyword, kbId);
            monitor.end("获取用户上传文件成功");

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "获取用户上传文件列表成功");
            response.put("data", PageResult.fromList(fileData, PageQuery.of(page, size, cursor)));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_USER_UPLOADED_FILES", userId, "获取用户上传文件失败", e);
            monitor.end("获取用户上传文件失败: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("message", "获取用户上传文件列表失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 解析时间范围参数
     * 支持格式：近7天、近30天、近90天、ISO 8601日期、天数数字
     */
    private LocalDateTime parseTimeRange(String timeRange) {
        if (timeRange == null || timeRange.isEmpty()) return null;
        
        switch (timeRange) {
            case "近7天":
                return LocalDateTime.now().minusDays(7);
            case "近30天":
                return LocalDateTime.now().minusDays(30);
            case "近90天":
                return LocalDateTime.now().minusDays(90);
            default:
                try {
                    // 尝试解析为天数数字
                    long days = Long.parseLong(timeRange);
                    return LocalDateTime.now().minusDays(days);
                } catch (NumberFormatException e) {
                    try {
                        // 尝试解析为 ISO 8601 日期
                        return LocalDateTime.parse(timeRange);
                    } catch (Exception ex) {
                        return null;
                    }
                }
        }
    }
    
    /**
     * 根据文件名下载文件
     * 
     * @param fileName 文件名
     * @param token JWT token
     * @return 文件资源或错误响应
     */
    @GetMapping("/download")
    public ResponseEntity<?> downloadFileByName(
            @RequestParam String fileName,
            @RequestParam(required = false) String token) {
        
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("DOWNLOAD_FILE_BY_NAME");
        try {
            // 验证token并获取用户信息
            String userId = null;
            String orgTags = null;
            
            if (token != null && !token.trim().isEmpty()) {
                try {
                    // 解析JWT token获取用户信息
                    // 注意：JWT中的sub字段存储用户名，userId字段存储用户ID（但有时可能存储的是用户名）
                    userId = jwtUtils.extractUsernameFromToken(token);
                    orgTags = jwtUtils.extractOrgTagsFromToken(token);
                } catch (Exception e) {
                    LogUtils.logBusiness("DOWNLOAD_FILE_BY_NAME", "anonymous", "Token解析失败: fileName=%s", fileName);
                }
            }
            
            LogUtils.logBusiness("DOWNLOAD_FILE_BY_NAME", userId != null ? userId : "anonymous", "接收到文件下载请求: fileName=%s", fileName);
            
            // 如果没有提供token或token无效，只允许下载公开文件
            if (userId == null) {
                // 查找公开文件
                Optional<FileUpload> publicFile = fileUploadRepository.findByFileNameAndIsPublicTrue(fileName);
                if (publicFile.isEmpty()) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("code", HttpStatus.NOT_FOUND.value());
                    response.put("message", "文件不存在或需要登录访问");
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                }
                
                FileUpload file = publicFile.get();
                String downloadUrl = documentService.generateDownloadUrl(file.getFileMd5());
                
                if (downloadUrl == null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
                    response.put("message", "无法生成下载链接");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
                
                Map<String, Object> response = new HashMap<>();
                response.put("code", 200);
                response.put("message", "文件下载链接生成成功");
                response.put("data", Map.of(
                    "fileName", file.getFileName(),
                    "downloadUrl", downloadUrl,
                    "fileSize", file.getTotalSize()
                ));
                return ResponseEntity.ok(response);
            }
            
            // 有token的情况，查找用户可访问的文件
            List<FileUpload> accessibleFiles = documentService.getAccessibleFiles(userId, orgTags);
            
            // 根据文件名查找匹配的文件
            Optional<FileUpload> targetFile = accessibleFiles.stream()
                    .filter(file -> file.getFileName().equals(fileName))
                    .findFirst();
                    
            if (targetFile.isEmpty()) {
                LogUtils.logUserOperation(userId, "DOWNLOAD_FILE_BY_NAME", fileName, "FAILED_NOT_FOUND");
                monitor.end("下载失败：文件不存在或无权限访问");
                Map<String, Object> response = new HashMap<>();
                response.put("code", HttpStatus.NOT_FOUND.value());
                response.put("message", "文件不存在或无权限访问");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            FileUpload file = targetFile.get();
            
            // 生成下载链接或返回预签名URL
            String downloadUrl = documentService.generateDownloadUrl(file.getFileMd5());
            
            if (downloadUrl == null) {
                LogUtils.logUserOperation(userId, "DOWNLOAD_FILE_BY_NAME", fileName, "FAILED_GENERATE_URL");
                monitor.end("下载失败：无法生成下载链接");
                Map<String, Object> response = new HashMap<>();
                response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
                response.put("message", "无法生成下载链接");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
            
            LogUtils.logFileOperation(userId, "DOWNLOAD", file.getFileName(), file.getFileMd5(), "SUCCESS");
            LogUtils.logUserOperation(userId, "DOWNLOAD_FILE_BY_NAME", fileName, "SUCCESS");
            monitor.end("文件下载链接生成成功");
            
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "文件下载链接生成成功");
            response.put("data", Map.of(
                "fileName", file.getFileName(),
                "downloadUrl", downloadUrl,
                "fileSize", file.getTotalSize()
            ));
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            String userId = "unknown";
            try {
                if (token != null && !token.trim().isEmpty()) {
                    userId = jwtUtils.extractUsernameFromToken(token);
                }
            } catch (Exception ignored) {}
            
            LogUtils.logBusinessError("DOWNLOAD_FILE_BY_NAME", userId, "文件下载失败: fileName=%s", e, fileName);
            monitor.end("下载失败: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("message", "文件下载失败: " + e.getMessage()); 
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 预览文件内容
     *
     * @param fileName 文件名
     * @param fileMd5 文件MD5（可选，用于精确定位同名文件）
     * @param token JWT token (URL参数，用于向后兼容)
     * @return 文件预览内容或错误响应
     */
    @GetMapping("/preview")
    public ResponseEntity<?> previewFileByName(
            @RequestParam String fileName,
            @RequestParam(required = false) String fileMd5,
            @RequestParam(required = false) String token) {
        
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("PREVIEW_FILE_BY_NAME");
        try {
            // 验证token并获取用户信息
            String userId = null;
            String orgTags = null;
            
            // 优先从Spring Security上下文获取已认证的用户信息
            try {
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication != null && authentication.isAuthenticated() 
                    && authentication.getPrincipal() instanceof UserDetails userDetails) {
                    userId = userDetails.getUsername();
                    // 从userDetails中获取组织标签信息
                    orgTags = userDetails.getAuthorities().stream()
                        .map(auth -> auth.getAuthority().replace("ROLE_", ""))
                        .findFirst()
                        .orElse(null);
                }
            } catch (Exception e) {
                LogUtils.logBusiness("PREVIEW_FILE_BY_NAME", "anonymous", "Security上下文获取失败: fileName=%s", fileName);
            }
            
            // 如果Security上下文中没有用户信息，尝试从URL参数token中获取
            if (userId == null && token != null && !token.trim().isEmpty()) {
                try {
                    userId = jwtUtils.extractUsernameFromToken(token);
                    orgTags = jwtUtils.extractOrgTagsFromToken(token);
                } catch (Exception e) {
                    LogUtils.logBusiness("PREVIEW_FILE_BY_NAME", "anonymous", "Token解析失败: fileName=%s", fileName);
                }
            }
            
            LogUtils.logBusiness("PREVIEW_FILE_BY_NAME", userId != null ? userId : "anonymous", "接收到文件预览请求: fileName=%s, fileMd5=%s", fileName, fileMd5);

            FileUpload file = null;

            // 如果没有提供token或token无效，只允许预览公开文件
            if (userId == null) {
                // 优先使用MD5查找（如果提供）
                if (fileMd5 != null && !fileMd5.trim().isEmpty()) {
                    Optional<FileUpload> fileByMd5 = fileUploadRepository.findByFileMd5AndIsPublicTrue(fileMd5);
                    if (fileByMd5.isPresent()) {
                        file = fileByMd5.get();
                        LogUtils.logBusiness("PREVIEW_FILE_BY_NAME", "anonymous", "使用MD5找到公开文件: fileMd5=%s", fileMd5);
                    }
                }

                // 如果MD5未找到或未提供，降级到文件名查找
                if (file == null) {
                    Optional<FileUpload> publicFile = fileUploadRepository.findByFileNameAndIsPublicTrue(fileName);
                    if (publicFile.isPresent()) {
                        file = publicFile.get();
                        LogUtils.logBusiness("PREVIEW_FILE_BY_NAME", "anonymous", "使用文件名找到公开文件: fileName=%s", fileName);
                    }
                }

                if (file == null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("code", HttpStatus.NOT_FOUND.value());
                    response.put("message", "文件不存在或需要登录访问");
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                }

                String previewContent = documentService.getFilePreviewContent(file.getFileMd5(), file.getFileName());

                if (previewContent == null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
                    response.put("message", "无法获取文件预览内容");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }

                Map<String, Object> response = new HashMap<>();
                response.put("code", 200);
                response.put("message", "文件预览内容获取成功");
                response.put("data", Map.of(
                    "fileName", file.getFileName(),
                    "content", previewContent,
                    "fileSize", file.getTotalSize()
                ));
                return ResponseEntity.ok(response);
            }

            // 有token的情况，查找用户可访问的文件
            List<FileUpload> accessibleFiles = documentService.getAccessibleFiles(userId, orgTags);

            // 优先使用MD5查找（如果提供）
            Optional<FileUpload> targetFile = Optional.empty();
            if (fileMd5 != null && !fileMd5.trim().isEmpty()) {
                final String md5 = fileMd5;
                targetFile = accessibleFiles.stream()
                        .filter(f -> f.getFileMd5().equals(md5))
                        .findFirst();
                if (targetFile.isPresent()) {
                    LogUtils.logBusiness("PREVIEW_FILE_BY_NAME", userId, "使用MD5找到文件: fileMd5=%s", fileMd5);
                }
            }

            // 如果MD5未找到或未提供，降级到文件名查找
            if (targetFile.isEmpty()) {
                targetFile = accessibleFiles.stream()
                        .filter(f -> f.getFileName().equals(fileName))
                        .findFirst();
                if (targetFile.isPresent()) {
                    LogUtils.logBusiness("PREVIEW_FILE_BY_NAME", userId, "使用文件名找到文件: fileName=%s", fileName);
                }
            }

            if (targetFile.isEmpty()) {
                LogUtils.logUserOperation(userId, "PREVIEW_FILE_BY_NAME", fileName, "FAILED_NOT_FOUND");
                monitor.end("预览失败：文件不存在或无权限访问");
                Map<String, Object> response = new HashMap<>();
                response.put("code", HttpStatus.NOT_FOUND.value());
                response.put("message", "文件不存在或无权限访问");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            file = targetFile.get();
            
            // 获取文件预览内容
            String previewContent = documentService.getFilePreviewContent(file.getFileMd5(), file.getFileName());
            
            if (previewContent == null) {
                LogUtils.logUserOperation(userId, "PREVIEW_FILE_BY_NAME", fileName, "FAILED_GET_CONTENT");
                monitor.end("预览失败：无法获取文件内容");
                Map<String, Object> response = new HashMap<>();
                response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
                response.put("message", "无法获取文件预览内容");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
            
            LogUtils.logFileOperation(userId, "PREVIEW", file.getFileName(), file.getFileMd5(), "SUCCESS");
            LogUtils.logUserOperation(userId, "PREVIEW_FILE_BY_NAME", fileName, "SUCCESS");
            monitor.end("文件预览内容获取成功");
            
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "文件预览内容获取成功");
            response.put("data", Map.of(
                "fileName", file.getFileName(),
                "content", previewContent,
                "fileSize", file.getTotalSize()
            ));
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            String userId = "unknown";
            try {
                if (token != null && !token.trim().isEmpty()) {
                    userId = jwtUtils.extractUsernameFromToken(token);
                }
            } catch (Exception ignored) {}
            
            LogUtils.logBusinessError("PREVIEW_FILE_BY_NAME", userId, "文件预览失败: fileName=%s", e, fileName);
            monitor.end("预览失败: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("message", "文件预览失败: " + e.getMessage()); 
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 根据文件 MD5 下载文件（精确定位，避免同名文件歧义）
     *
     * @param fileMd5 文件 MD5
     * @param token   JWT token（可选，用于 URL 直接访问场景）
     * @return 预签名下载链接
     */
    @GetMapping("/download-by-md5")
    public ResponseEntity<?> downloadFileByMd5(
            @RequestParam String fileMd5,
            @RequestParam(required = false) String token) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("DOWNLOAD_FILE_BY_MD5");
        try {
            // 从 Security 上下文或 token 参数中解析用户ID和组织标签
            String userId = null;
            String orgTags = null;

            // 优先从 Spring Security 上下文获取已认证的用户信息
            try {
                var authentication = org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication();
                if (authentication != null && authentication.isAuthenticated()
                        && authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
                    userId = userDetails.getUsername();
                    // 从 userDetails 中获取组织标签信息
                    orgTags = userDetails.getAuthorities().stream()
                            .map(auth -> auth.getAuthority().replace("ROLE_", ""))
                            .findFirst()
                            .orElse(null);
                }
            } catch (Exception ignored) {}

            // 如果 Security 上下文中没有用户信息，尝试从 URL 参数 token 中获取
            if (userId == null && token != null && !token.trim().isEmpty()) {
                try {
                    userId = jwtUtils.extractUsernameFromToken(token);
                    orgTags = jwtUtils.extractOrgTagsFromToken(token);
                } catch (Exception ignored) {}
            }

            LogUtils.logBusiness("DOWNLOAD_FILE_BY_MD5", userId != null ? userId : "anonymous",
                    "接收到按MD5下载文件请求: fileMd5=%s", fileMd5);

            // 未登录用户：只允许下载公开文件
            if (userId == null) {
                Optional<FileUpload> publicFile = fileUploadRepository.findByFileMd5AndIsPublicTrue(fileMd5);
                if (publicFile.isEmpty()) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("code", HttpStatus.NOT_FOUND.value());
                    response.put("message", "文件不存在或需要登录访问");
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                }
                FileUpload file = publicFile.get();
                String downloadUrl = documentService.generateDownloadUrl(file.getFileMd5());
                if (downloadUrl == null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
                    response.put("message", "无法生成下载链接");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
                LogUtils.logFileOperation("anonymous", "DOWNLOAD_BY_MD5", file.getFileName(), fileMd5, "SUCCESS");
                monitor.end("匿名用户按MD5生成公开文件下载链接成功");
                Map<String, Object> response = new HashMap<>();
                response.put("code", 200);
                response.put("message", "文件下载链接生成成功");
                response.put("data", Map.of(
                        "fileName", file.getFileName(),
                        "downloadUrl", downloadUrl,
                        "fileSize", file.getTotalSize()
                ));
                return ResponseEntity.ok(response);
            }

            // 已登录用户：通过权限校验确认该用户有权访问此文件
            List<FileUpload> accessibleFiles = documentService.getAccessibleFiles(userId, orgTags);
            Optional<FileUpload> targetFile = accessibleFiles.stream()
                    .filter(f -> f.getFileMd5().equals(fileMd5))
                    .findFirst();

            if (targetFile.isEmpty()) {
                LogUtils.logUserOperation(userId, "DOWNLOAD_BY_MD5", fileMd5, "FAILED_NOT_FOUND");
                monitor.end("下载失败：文件不存在或无权限访问");
                Map<String, Object> response = new HashMap<>();
                response.put("code", HttpStatus.NOT_FOUND.value());
                response.put("message", "文件不存在或无权限访问");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            FileUpload file = targetFile.get();
            String downloadUrl = documentService.generateDownloadUrl(file.getFileMd5());
            if (downloadUrl == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("code", HttpStatus.NOT_FOUND.value());
                response.put("message", "文件不存在或无法生成下载链接");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            LogUtils.logFileOperation(userId, "DOWNLOAD_BY_MD5", file.getFileName(), fileMd5, "SUCCESS");
            monitor.end("按MD5生成下载链接成功");

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "文件下载链接生成成功");
            response.put("data", Map.of(
                    "fileName", file.getFileName(),
                    "downloadUrl", downloadUrl,
                    "fileSize", file.getTotalSize()
            ));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LogUtils.logBusinessError("DOWNLOAD_FILE_BY_MD5", "unknown", "按MD5下载文件失败: fileMd5=%s", e, fileMd5);
            monitor.end("下载失败: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("message", "文件下载失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 根据tagId获取tagName
     *
     * @param tagId 组织标签ID
     * @return 组织标签名称，如果找不到则返回原tagId
     */
    private String getOrgTagName(String tagId) {
        if (tagId == null || tagId.isEmpty()) {
            return null;
        }

        try {
            Optional<OrganizationTag> tagOpt = organizationTagRepository.findByTagId(tagId);
            if (tagOpt.isPresent()) {
                return tagOpt.get().getName();
            } else {
                LogUtils.logBusiness("GET_ORG_TAG_NAME", "system", "找不到组织标签: tagId=%s", tagId);
                return tagId; // 如果找不到标签名称，返回原tagId
            }
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_ORG_TAG_NAME", "system", "查询组织标签名称失败: tagId=%s", e, tagId);
            return tagId; // 发生错误时返回原tagId
        }
    }

    /**
     * 从文件名提取扩展名
     */
    private String extractFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }

    /**
     * 将 FileUpload.status (int: 0=上传中, 1=已完成) 映射为前端 DocStatus
     * - 0/pending: 上传中或等待解析
     * - 1/done: 已完成（前端实际通过 chunkCount > 0 判断是否已解析）
     * 注意：processing 状态由解析任务队列驱动，前端通过轮询文档列表发现状态变化
     */
    private String mapFileStatus(int status) {
        // status 0 = 上传中/待解析，status 1 = 已完成
        return status == 1 ? "done" : "pending";
    }

    /**
     * 根据 kbId 获取知识库名称
     */
    private String getKnowledgeBaseName(String kbId) {
        if (kbId == null || kbId.isEmpty()) {
            return null;
        }
        try {
            Optional<com.smart.kf.model.KnowledgeBase> kbOpt = knowledgeBaseRepository.findByKbId(kbId);
            return kbOpt.map(com.smart.kf.model.KnowledgeBase::getName).orElse(null);
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_KB_NAME", "system", "查询知识库名称失败: kbId=%s", e, kbId);
            return null;
        }
    }
} 
