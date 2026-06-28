package com.smart.kf.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.kf.config.KafkaConfig;
import com.smart.kf.exception.CustomException;
import com.smart.kf.model.*;
import com.smart.kf.repository.ConversationRepository;
import com.smart.kf.repository.FileUploadRepository;
import com.smart.kf.repository.KnowledgeBaseRepository;
import com.smart.kf.repository.OrganizationTagRepository;
import com.smart.kf.repository.PermissionRepository;
import com.smart.kf.repository.RoleRepository;
import com.smart.kf.repository.UserFavoriteRepository;
import com.smart.kf.repository.UserRepository;
import com.smart.kf.service.DocumentService;
import com.smart.kf.service.FileTypeValidationService;
import com.smart.kf.service.I18nTranslationService;
import com.smart.kf.service.RbacService;
import com.smart.kf.service.SystemActivityService;
import com.smart.kf.service.UserService;
import com.smart.kf.utils.JwtUtils;
import com.smart.kf.utils.LogUtils;
import com.smart.kf.utils.MinioMigrationUtil;
import com.smart.kf.utils.PasswordUtil;
import com.smart.kf.utils.pagination.PageQuery;
import com.smart.kf.utils.pagination.PageResult;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.lang.management.ManagementFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Comparator;

/**
 * 管理员控制器，提供管理知识库、查看系统状态和监控用户活动的接口
 * 全部接口需要 system:admin 权限（在 SecurityConfig 路由级保护，此处方法级双重保障）
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAuthority('system:admin')")
public class AdminController {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RbacService rbacService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserService userService;

    @Autowired
    private I18nTranslationService i18nTranslationService;

    @Autowired
    private OrganizationTagRepository organizationTagRepository;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MinioMigrationUtil migrationUtil;

    @Autowired
    private FileUploadRepository fileUploadRepository;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    @Qualifier("minioPublicUrl")
    private String minioPublicUrl;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private KafkaConfig kafkaConfig;

    @Autowired
    private FileTypeValidationService fileTypeValidationService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private SystemActivityService systemActivityService;

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    private UserFavoriteRepository userFavoriteRepository;

    /**
     * 获取所有用户列表
     */
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String cursor) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("ADMIN_GET_ALL_USERS");
        String adminUsername = null;
        try {
            adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            validateAdmin(adminUsername);
            
            LogUtils.logBusiness("ADMIN_GET_ALL_USERS", adminUsername, "管理员开始获取所有用户列表");
            
            List<User> users = userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
            // 移除敏感信息
            users.forEach(user -> user.setPassword(null));
            
            LogUtils.logUserOperation(adminUsername, "ADMIN_GET_ALL_USERS", "user_list", "SUCCESS");
            LogUtils.logBusiness("ADMIN_GET_ALL_USERS", adminUsername, "成功获取用户列表，用户数量: %d", users.size());
            monitor.end("获取用户列表成功");
            
            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "Get all users successful",
                "data", PageResult.fromList(users, PageQuery.of(page, size, cursor))
            ));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_ALL_USERS", adminUsername, "获取所有用户失败", e);
            monitor.end("获取用户列表失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "Failed to get users: " + e.getMessage()));
        }
    }

    /**
     * 更新用户信息（用户名、邮箱、角色）
     */
    @PutMapping("/users/{userId}")
    public ResponseEntity<?> updateUser(
            @RequestHeader("Authorization") String token,
            @PathVariable Long userId,
            @RequestBody Map<String, Object> request) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        try {
            validateAdmin(adminUsername);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CustomException("用户不存在", HttpStatus.NOT_FOUND));
            if (request.containsKey("username")) user.setUsername((String) request.get("username"));
            if (request.containsKey("email")) user.setEmail((String) request.get("email"));
            if (request.containsKey("role")) {
                user.setRole(User.Role.valueOf(((String) request.get("role")).toUpperCase()));
            }
            userRepository.save(user);
            user.setPassword(null);
            LogUtils.logUserOperation(adminUsername, "ADMIN_UPDATE_USER", "user:" + userId, "SUCCESS");
            return ResponseEntity.ok(Map.of("code", 200, "message", "用户信息更新成功", "data", user));
        } catch (CustomException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_UPDATE_USER", adminUsername, "更新用户失败: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "更新用户失败: " + e.getMessage()));
        }
    }

    /**
     * 删除用户（不允许删除管理员账号）
     */
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(
            @RequestHeader("Authorization") String token,
            @PathVariable Long userId) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        try {
            validateAdmin(adminUsername);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CustomException("用户不存在", HttpStatus.NOT_FOUND));
            if (user.getRole() == User.Role.ADMIN) {
                throw new CustomException("不能删除管理员账号", HttpStatus.FORBIDDEN);
            }
            User admin = userRepository.findByUsername(adminUsername)
                    .orElseThrow(() -> new CustomException("管理员账号不存在", HttpStatus.NOT_FOUND));

            // 删除用户私有数据
            userFavoriteRepository.deleteAll(userFavoriteRepository.findByUserOrderByUpdatedAtDesc(user));
            conversationRepository.deleteAll(conversationRepository.findByUserId(userId));

            // 将该用户创建的共享资源转让给执行删除的管理员
            List<OrganizationTag> ownedTags = organizationTagRepository.findByCreatedBy(user);
            ownedTags.forEach(tag -> tag.setCreatedBy(admin));
            organizationTagRepository.saveAll(ownedTags);

            List<KnowledgeBase> ownedKbs = knowledgeBaseRepository.findByCreatedBy(user);
            ownedKbs.forEach(kb -> kb.setCreatedBy(admin));
            knowledgeBaseRepository.saveAll(ownedKbs);

            userRepository.delete(user);
            LogUtils.logUserOperation(adminUsername, "ADMIN_DELETE_USER", "user:" + userId, "SUCCESS");
            return ResponseEntity.ok(Map.of("code", 200, "message", "用户删除成功"));
        } catch (CustomException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_DELETE_USER", adminUsername, "删除用户失败: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "删除用户失败: " + e.getMessage()));
        }
    }

    /**
     * 重置用户密码（生成随机12位密码并返回，管理员转告用户）
     */
    @PostMapping("/users/{userId}/reset-password")
    public ResponseEntity<?> resetUserPassword(
            @RequestHeader("Authorization") String token,
            @PathVariable Long userId) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        try {
            validateAdmin(adminUsername);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CustomException("用户不存在", HttpStatus.NOT_FOUND));
            String newPassword = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            user.setPassword(PasswordUtil.encode(newPassword));
            userRepository.save(user);
            LogUtils.logUserOperation(adminUsername, "ADMIN_RESET_PASSWORD", "user:" + userId, "SUCCESS");
            return ResponseEntity.ok(Map.of("code", 200, "message", "密码重置成功", "data", Map.of("newPassword", newPassword)));
        } catch (CustomException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_RESET_PASSWORD", adminUsername, "重置密码失败: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "重置密码失败: " + e.getMessage()));
        }
    }

    /**
     * 添加知识库文档
     */
    @PostMapping("/knowledge/add")
    public ResponseEntity<?> addKnowledgeDocument(
            @RequestHeader("Authorization") String token,
            @RequestParam("file") MultipartFile file,
            @RequestParam("description") String description) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("ADMIN_ADD_KNOWLEDGE");
        String adminUsername = null;
        try {
            adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            validateAdmin(adminUsername);

            String originalFilename = file.getOriginalFilename();
            LogUtils.logBusiness("ADMIN_ADD_KNOWLEDGE", adminUsername, 
                "管理员添加知识库文档: fileName=%s, description=%s", originalFilename, description);

            // 1. 验证文件类型
            if (originalFilename != null && !originalFilename.isEmpty()) {
                FileTypeValidationService.FileTypeValidationResult validationResult = 
                    fileTypeValidationService.validateFileType(originalFilename);
                if (!validationResult.isValid()) {
                    LogUtils.logBusinessError("ADMIN_ADD_KNOWLEDGE", adminUsername, 
                        "文件类型验证失败: fileName=%s, message=%s", 
                        new RuntimeException(validationResult.getMessage()), 
                        originalFilename, validationResult.getMessage());
                    monitor.end("文件类型验证失败");
                    return ResponseEntity.badRequest()
                        .body(Map.of("code", 400, "message", validationResult.getMessage(),
                            "supportedTypes", fileTypeValidationService.getSupportedFileTypes()));
                }
            }

            // 2. 计算文件 MD5
            byte[] fileBytes = file.getBytes();
            String fileMd5 = DigestUtils.md5Hex(fileBytes);

            // 3. 检查文件是否已存在（通过 MD5 去重）
            Optional<FileUpload> existing = fileUploadRepository.findByFileMd5(fileMd5);
            if (existing.isPresent()) {
                LogUtils.logBusiness("ADMIN_ADD_KNOWLEDGE", adminUsername, 
                    "文件已存在: fileMd5=%s, fileName=%s", fileMd5, originalFilename);
                monitor.end("文件已存在，跳过上传");
                return ResponseEntity.ok(Map.of(
                    "code", 200, 
                    "message", "文件已存在于知识库中",
                    "data", Map.of("fileMd5", fileMd5, "fileName", existing.get().getFileName())
                ));
            }

            // 4. 上传文件到 MinIO
            String objectPath = "merged/" + fileMd5;
            long fileSize = file.getSize();
            String contentType = file.getContentType();

            LogUtils.logBusiness("ADMIN_ADD_KNOWLEDGE", adminUsername, 
                "开始上传文件到MinIO: fileMd5=%s, fileName=%s, size=%d, contentType=%s", 
                fileMd5, originalFilename, fileSize, contentType);

            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket("uploads")
                    .object(objectPath)
                    .stream(new ByteArrayInputStream(fileBytes), fileSize, -1)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build()
            );

            LogUtils.logBusiness("ADMIN_ADD_KNOWLEDGE", adminUsername, 
                "文件上传到MinIO成功: fileMd5=%s, path=%s", fileMd5, objectPath);

            // 5. 创建 FileUpload 记录
            FileUpload fileUpload = new FileUpload();
            fileUpload.setFileMd5(fileMd5);
            fileUpload.setFileName(originalFilename);
            fileUpload.setTotalSize(fileSize);
            fileUpload.setStatus(1); // 已完成
            fileUpload.setUserId(adminUsername);
            fileUpload.setOrgTag("admin");
            fileUpload.setPublic(true);
            fileUploadRepository.save(fileUpload);

            LogUtils.logFileOperation(adminUsername, "ADD_KNOWLEDGE", originalFilename, fileMd5, "SUCCESS");

            // 6. 发送 Kafka 文件处理任务（向量化、ES 索引）
            String objectUrl = minioPublicUrl + "/uploads/" + objectPath;
            
            // 检查 Kafka 是否可用
            try {
                FileProcessingTask task = new FileProcessingTask(
                    fileMd5,
                    objectUrl,
                    originalFilename,
                    adminUsername,
                    "admin",
                    true,
                    null
                );
                
                LogUtils.logBusiness("ADMIN_ADD_KNOWLEDGE", adminUsername, 
                    "发送文件处理任务到Kafka: topic=%s, fileMd5=%s", 
                    kafkaConfig.getFileProcessingTopic(), fileMd5);
                    
                kafkaTemplate.executeInTransaction(kt -> {
                    kt.send(kafkaConfig.getFileProcessingTopic(), task);
                    return true;
                });
                
                LogUtils.logBusiness("ADMIN_ADD_KNOWLEDGE", adminUsername, 
                    "Kafka任务发送成功: fileMd5=%s", fileMd5);
            } catch (Exception kafkaEx) {
                LogUtils.logBusinessError("ADMIN_ADD_KNOWLEDGE", adminUsername, 
                    "Kafka任务发送失败（文件已保存，仅跳过索引）: fileMd5=%s", kafkaEx, fileMd5);
            }

            monitor.end("知识库文档添加成功");

            return ResponseEntity.ok(Map.of(
                "code", 200, 
                "message", "文档已成功添加到知识库",
                "data", Map.of(
                    "fileMd5", fileMd5,
                    "fileName", originalFilename != null ? originalFilename : "unknown",
                    "fileSize", fileSize,
                    "description", description
                )
            ));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_ADD_KNOWLEDGE", adminUsername, "添加知识库文档失败", e);
            monitor.end("添加知识库文档失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "添加文档失败: " + e.getMessage()));
        }
    }

    /**
     * 删除知识库文档
     */
    @DeleteMapping("/knowledge/{documentId}")
    public ResponseEntity<?> deleteKnowledgeDocument(
            @RequestHeader("Authorization") String token,
            @PathVariable("documentId") String documentId) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("ADMIN_DELETE_KNOWLEDGE");
        String adminUsername = null;
        try {
            adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            validateAdmin(adminUsername);
            
            LogUtils.logBusiness("ADMIN_DELETE_KNOWLEDGE", adminUsername, 
                "管理员删除知识库文档: documentId=%s", documentId);
            
            // 查找文件记录
            Optional<FileUpload> fileOpt = fileUploadRepository.findByFileMd5(documentId);
            if (fileOpt.isEmpty()) {
                LogUtils.logBusiness("ADMIN_DELETE_KNOWLEDGE", adminUsername, 
                    "文档不存在: documentId=%s", documentId);
                monitor.end("删除失败：文档不存在");
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("code", 404, "message", "文档不存在"));
            }
            
            FileUpload file = fileOpt.get();
            String fileMd5 = file.getFileMd5();
            String fileName = file.getFileName();
            String fileUserId = file.getUserId();
            
            LogUtils.logBusiness("ADMIN_DELETE_KNOWLEDGE", adminUsername, 
                "找到文档: fileMd5=%s, fileName=%s, fileUserId=%s", fileMd5, fileName, fileUserId);
            
            // 调用 DocumentService 删除文档及其所有关联数据
            documentService.deleteDocument(fileMd5, fileUserId);
            
            LogUtils.logFileOperation(adminUsername, "DELETE_KNOWLEDGE", fileName, fileMd5, "SUCCESS");
            LogUtils.logBusiness("ADMIN_DELETE_KNOWLEDGE", adminUsername, 
                "文档删除成功: fileMd5=%s, fileName=%s", fileMd5, fileName);
            monitor.end("知识库文档删除成功");
            
            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "文档已成功从知识库中删除",
                "data", Map.of(
                    "fileMd5", fileMd5,
                    "fileName", fileName
                )
            ));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_DELETE_KNOWLEDGE", adminUsername, "删除知识库文档失败", e);
            monitor.end("删除知识库文档失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "删除文档失败: " + e.getMessage()));
        }
    }

    /**
     * 获取系统状态
     */
    @GetMapping("/system/status")
    public ResponseEntity<?> getSystemStatus(@RequestHeader("Authorization") String token) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("ADMIN_GET_SYSTEM_STATUS");
        String adminUsername = null;
        try {
            adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            validateAdmin(adminUsername);
            
            LogUtils.logBusiness("ADMIN_GET_SYSTEM_STATUS", adminUsername, "管理员获取系统状态");

            Map<String, Object> status = new LinkedHashMap<>();

            // 1. JVM 内存使用情况
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            double memoryUsagePercent = maxMemory > 0 ? (usedMemory * 100.0 / maxMemory) : 0;
            status.put("jvm_memory_max_mb", maxMemory / (1024 * 1024));
            status.put("jvm_memory_used_mb", usedMemory / (1024 * 1024));
            status.put("jvm_memory_free_mb", freeMemory / (1024 * 1024));
            status.put("memory_usage", String.format("%.1f%%", memoryUsagePercent));

            // 2. CPU 使用情况
            try {
                java.lang.management.OperatingSystemMXBean osMxBean = 
                    ManagementFactory.getOperatingSystemMXBean();
                if (osMxBean instanceof com.sun.management.OperatingSystemMXBean sunOsMxBean) {
                    double cpuLoad = sunOsMxBean.getCpuLoad();
                    double processCpuLoad = sunOsMxBean.getProcessCpuLoad();
                    status.put("cpu_usage", String.format("%.1f%%", 
                        cpuLoad >= 0 ? cpuLoad * 100 : 0));
                    status.put("process_cpu_usage", String.format("%.1f%%", 
                        processCpuLoad >= 0 ? processCpuLoad * 100 : 0));
                } else {
                    double systemLoad = osMxBean.getSystemLoadAverage();
                    status.put("cpu_usage", String.format("系统负载: %.2f", systemLoad));
                    status.put("available_processors", Runtime.getRuntime().availableProcessors());
                }
            } catch (Exception e) {
                LogUtils.logBusinessError("ADMIN_GET_SYSTEM_STATUS", adminUsername, "获取CPU状态失败", e);
                status.put("cpu_usage", "N/A");
            }

            // 3. 磁盘使用情况
            try {
                java.io.File root = new java.io.File("/");
                long totalDisk = root.getTotalSpace();
                long freeDisk = root.getFreeSpace();
                long usedDisk = totalDisk - freeDisk;
                double diskUsagePercent = totalDisk > 0 ? (usedDisk * 100.0 / totalDisk) : 0;
                status.put("disk_total_gb", String.format("%.1f", totalDisk / (1024.0 * 1024 * 1024)));
                status.put("disk_free_gb", String.format("%.1f", freeDisk / (1024.0 * 1024 * 1024)));
                status.put("disk_usage", String.format("%.1f%%", diskUsagePercent));
            } catch (Exception e) {
                LogUtils.logBusinessError("ADMIN_GET_SYSTEM_STATUS", adminUsername, "获取磁盘状态失败", e);
                status.put("disk_usage", "N/A");
            }

            // 4. 数据库统计数据
            long totalUsers = userRepository.count();
            long totalFiles = fileUploadRepository.count();
            long totalOrgTags = organizationTagRepository.count();
            
            // 统计对话数
            long totalConversations = 0;
            try {
                Set<String> conversationKeys = redisTemplate.keys("conversation:*");
                totalConversations = conversationKeys.size();
            } catch (Exception e) {
                LogUtils.logBusinessError("ADMIN_GET_SYSTEM_STATUS", adminUsername, "统计对话数失败", e);
            }

            // 统计活跃用户（今日有活动的用户）
            long activeUsers = 0;
            try {
                Set<String> userKeys = redisTemplate.keys("user:*:current_conversation");
                activeUsers = userKeys.size();
            } catch (Exception e) {
                LogUtils.logBusinessError("ADMIN_GET_SYSTEM_STATUS", adminUsername, "统计活跃用户失败", e);
            }
            
            status.put("active_users", activeUsers);
            status.put("total_users", totalUsers);
            status.put("total_documents", totalFiles);
            status.put("total_conversations", totalConversations);
            status.put("total_org_tags", totalOrgTags);

            // 5. 运行信息
            status.put("available_processors", Runtime.getRuntime().availableProcessors());
            status.put("java_version", System.getProperty("java.version"));
            status.put("os_name", System.getProperty("os.name"));
            status.put("os_arch", System.getProperty("os.arch"));

            LogUtils.logBusiness("ADMIN_GET_SYSTEM_STATUS", adminUsername, 
                "系统状态获取成功: memory=%s, cpu=%s, disk=%s, users=%d, files=%d",
                status.get("memory_usage"), status.get("cpu_usage"), 
                status.get("disk_usage"), totalUsers, totalFiles);
            monitor.end("获取系统状态成功");

            return ResponseEntity.ok(Map.of("code", 200, "message", "获取系统状态成功", "data", status));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_SYSTEM_STATUS", adminUsername, "获取系统状态失败", e);
            monitor.end("获取系统状态失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取系统状态失败: " + e.getMessage()));
        }
    }

    /**
     * 获取系统统计数据（从数据库实时查询）
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getSystemStats(@RequestHeader("Authorization") String token) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("ADMIN_GET_SYSTEM_STATS");
        String adminUsername = null;
        try {
            adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            validateAdmin(adminUsername);
            
            LocalDate today = LocalDate.now();
            LocalDateTime todayStart = today.atStartOfDay();
            LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();

            // 从数据库查询真实数据
            long totalUsers = userRepository.count();
            long totalFiles = fileUploadRepository.count();
            long totalDocuments = fileUploadRepository.count(); // 文件即文档
            long totalConversations = conversationRepository.count();

            // Redis 中可能包含仅保存在聊天会话缓存中的最近会话，取两边较大值兼容旧数据
            try {
                Set<String> conversationKeys = redisTemplate.keys("conversation:*");
                totalConversations = Math.max(totalConversations, conversationKeys.size());
            } catch (Exception e) {
                LogUtils.logBusinessError("ADMIN_GET_SYSTEM_STATS", adminUsername, "统计对话数失败", e);
            }
            
            // 统计今日新增数据
            long todayUploads = 0;
            
            try {
                // 统计今日上传的文件
                List<FileUpload> recentFiles = fileUploadRepository.findAll();
                todayUploads = recentFiles.stream()
                    .filter(f -> f.getCreatedAt() != null && f.getCreatedAt().isAfter(todayStart))
                    .count();
            } catch (Exception e) {
                LogUtils.logBusinessError("ADMIN_GET_SYSTEM_STATS", adminUsername, "统计今日上传失败", e);
            }
            
            // 统计组织标签数量
            long totalOrgTags = organizationTagRepository.count();
            long dbTodayQuestions = conversationRepository.findByTimestampBetween(todayStart, tomorrowStart).size();
            long todayQuestions = Math.max(dbTodayQuestions, getRedisLong("analytics:qa:" + today));
            long hitCount = getRedisLong("analytics:kb:" + today + ":hit");
            long missCount = getRedisLong("analytics:kb:" + today + ":miss");
            long searchTotal = hitCount + missCount;
            double knowledgeHitRate = searchTotal > 0 ? hitCount * 100.0 / searchTotal : 0;
            long responseTimeCount = getRedisLong("analytics:response_time_count:" + today);
            long responseTimeSum = getRedisLong("analytics:response_time_sum:" + today);
            long averageResponseTimeMs = responseTimeCount > 0 ? Math.round((double) responseTimeSum / responseTimeCount) : 0;
            List<Map<String, Object>> usageTrends = buildUsageTrends(today);
            List<Map<String, Object>> popularQuestions = buildPopularQuestions(today);
            
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("totalUsers", totalUsers);
            stats.put("totalFiles", totalFiles);
            stats.put("totalDocuments", totalDocuments);
            stats.put("totalConversations", totalConversations);
            stats.put("totalOrgTags", totalOrgTags);
            stats.put("todayUploads", todayUploads);
            stats.put("todayConversations", todayQuestions);
            stats.put("todayQuestions", todayQuestions);
            stats.put("knowledgeHitRate", Math.round(knowledgeHitRate * 10.0) / 10.0);
            stats.put("averageResponseTimeMs", averageResponseTimeMs);
            stats.put("usageTrends", usageTrends);
            stats.put("popularQuestions", popularQuestions);
            
            LogUtils.logBusiness("ADMIN_GET_SYSTEM_STATS", adminUsername,
                "获取系统统计成功: users=%d, files=%d, conversations=%d, orgTags=%d, todayQuestions=%d",
                totalUsers, totalFiles, totalConversations, totalOrgTags, todayQuestions);
            monitor.end("获取系统统计成功");
            
            return ResponseEntity.ok(Map.of("code", 200, "message", "获取系统统计成功", "data", stats));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_SYSTEM_STATS", adminUsername, "获取系统统计失败", e);
            monitor.end("获取系统统计失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取系统统计失败: " + e.getMessage()));
        }
    }

    @GetMapping("/activities")
    public ResponseEntity<?> getRecentActivities(@RequestHeader("Authorization") String token) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("ADMIN_GET_RECENT_ACTIVITIES");
        String adminUsername = null;
        try {
            adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            validateAdmin(adminUsername);

            Map<String, Object> data = systemActivityService.getRecentActivities();
            monitor.end("获取最近活动成功");
            return ResponseEntity.ok(Map.of("code", 200, "message", "获取最近活动成功", "data", data));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_RECENT_ACTIVITIES", adminUsername, "获取最近活动失败", e);
            monitor.end("获取最近活动失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", 500, "message", "获取最近活动失败: " + e.getMessage()));
        }
    }

    /**
     * 获取用户活动日志（从数据库查询真实数据）
     */
    @GetMapping("/user-activities")
    public ResponseEntity<?> getUserActivities(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String username,
            @RequestParam(required = false, name = "start_date") String startDate,
            @RequestParam(required = false, name = "end_date") String endDate) {
        
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("ADMIN_GET_USER_ACTIVITIES");
        String adminUsername = null;
        try {
            adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            validateAdmin(adminUsername);
            
            LogUtils.logBusiness("ADMIN_GET_USER_ACTIVITIES", adminUsername, 
                "管理员查询用户活动: username=%s, startDate=%s, endDate=%s", 
                username, startDate, endDate);

            // 解析时间范围
            LocalDateTime startDateTime = null;
            LocalDateTime endDateTime = null;
            if (startDate != null && !startDate.trim().isEmpty()) {
                startDateTime = parseDateTime(startDate);
            }
            if (endDate != null && !endDate.trim().isEmpty()) {
                endDateTime = parseDateTime(endDate);
                // 结束日期设为当天结束
                if (endDateTime != null && endDateTime.getHour() == 0 && endDateTime.getMinute() == 0) {
                    endDateTime = endDateTime.plusDays(1);
                }
            }

            List<Map<String, Object>> activities = new ArrayList<>();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

            // 1. 从文件上传记录构建活动
            List<FileUpload> allFiles = fileUploadRepository.findAll();
            for (FileUpload file : allFiles) {
                // 筛选指定用户
                if (username != null && !username.trim().isEmpty()) {
                    if (!username.equals(file.getUserId())) {
                        continue;
                    }
                }
                // 时间筛选
                if (file.getCreatedAt() != null) {
                    if (startDateTime != null && file.getCreatedAt().isBefore(startDateTime)) {
                        continue;
                    }
                    if (endDateTime != null && file.getCreatedAt().isAfter(endDateTime)) {
                        continue;
                    }
                }

                // 添加文件上传活动
                Map<String, Object> activity = new LinkedHashMap<>();
                activity.put("username", file.getUserId());
                activity.put("action", "UPLOAD_FILE");
                activity.put("timestamp", file.getCreatedAt() != null ? 
                    file.getCreatedAt().format(fmt) : "未知时间");
                activity.put("resource", file.getFileName());
                activity.put("details", Map.of(
                    "fileMd5", file.getFileMd5(),
                    "fileSize", file.getTotalSize(),
                    "orgTag", file.getOrgTag() != null ? file.getOrgTag() : "",
                    "isPublic", file.isPublic()
                ));
                activities.add(activity);
            }

            // 2. 从用户注册记录构建活动
            List<User> allUsers = userRepository.findAll();
            for (User user : allUsers) {
                // 筛选指定用户
                if (username != null && !username.trim().isEmpty()) {
                    if (!username.equals(user.getUsername())) {
                        continue;
                    }
                }
                // 时间筛选
                if (user.getCreatedAt() != null) {
                    if (startDateTime != null && user.getCreatedAt().isBefore(startDateTime)) {
                        continue;
                    }
                    if (endDateTime != null && user.getCreatedAt().isAfter(endDateTime)) {
                        continue;
                    }
                }

                // 添加用户注册活动
                Map<String, Object> activity = new LinkedHashMap<>();
                activity.put("username", user.getUsername());
                activity.put("action", "REGISTER");
                activity.put("timestamp", user.getCreatedAt() != null ? 
                    user.getCreatedAt().format(fmt) : "未知时间");
                activity.put("resource", "user_account");
                activity.put("details", Map.of(
                    "role", user.getRole().name(),
                    "orgTags", user.getOrgTags() != null ? user.getOrgTags() : ""
                ));
                activities.add(activity);
            }

            // 3. 从 Redis 会话信息构建登录活动
            try {
                for (String userKey : redisTemplate.keys("user:*:current_conversation")) {
                    String redisUsername = userKey.replace("user:", "").replace(":current_conversation", "");
                    
                    // 筛选指定用户
                    if (username != null && !username.trim().isEmpty()) {
                        if (!username.equals(redisUsername)) {
                            continue;
                        }
                    }

                    // 添加会话活动（表示用户在活跃对话中）
                    String conversationId = redisTemplate.opsForValue().get(userKey);
                    if (conversationId != null) {
                        Map<String, Object> activity = new LinkedHashMap<>();
                        activity.put("username", redisUsername);
                        activity.put("action", "ACTIVE_SESSION");
                        activity.put("timestamp", LocalDateTime.now().format(fmt));
                        activity.put("resource", "conversation");
                        activity.put("details", Map.of(
                            "conversationId", conversationId
                        ));
                        activities.add(activity);
                    }
                }
            } catch (Exception e) {
                LogUtils.logBusinessError("ADMIN_GET_USER_ACTIVITIES", adminUsername, "获取Redis会话信息失败", e);
            }

            // 按时间倒序排列
            activities.sort((a, b) -> {
                String ta = (String) a.getOrDefault("timestamp", "");
                String tb = (String) b.getOrDefault("timestamp", "");
                return tb.compareTo(ta);
            });

            // 限制返回数量
            if (activities.size() > 500) {
                activities = activities.subList(0, 500);
            }

            LogUtils.logBusiness("ADMIN_GET_USER_ACTIVITIES", adminUsername, 
                "获取用户活动成功: total=%d", activities.size());
            monitor.end("获取用户活动成功");

            return ResponseEntity.ok(Map.of(
                "code", 200, 
                "message", "获取用户活动成功", 
                "data", Map.of(
                    "total", activities.size(),
                    "activities", activities
                )
            ));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_USER_ACTIVITIES", adminUsername, "获取用户活动失败", e);
            monitor.end("获取用户活动失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取用户活动失败: " + e.getMessage()));
        }
    }
    
    /**
     * 创建管理员用户
     */
    @PostMapping("/users/create-admin")
    public ResponseEntity<?> createAdminUser(
            @RequestHeader("Authorization") String token,
            @RequestBody AdminUserRequest request) {
        
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            userService.createAdminUser(request.username(), request.password(), adminUsername);
            return ResponseEntity.ok(Map.of("code", 200, "message", "管理员用户创建成功"));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_CREATE_ADMIN_USER", adminUsername, "创建管理员用户失败: %s", e, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_CREATE_ADMIN_USER", adminUsername, "创建管理员用户异常: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "创建管理员用户失败: " + e.getMessage()));
        }
    }
    
    /**
     * 创建组织标签
     */
    @PostMapping("/org-tags")
    public ResponseEntity<?> createOrganizationTag(
            @RequestHeader("Authorization") String token,
            @RequestBody OrgTagRequest request) {
        
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            OrganizationTag tag = userService.createOrganizationTag(
                request.tagId(), 
                request.name(), 
                request.description(), 
                request.parentTag(), 
                adminUsername
            );
            return ResponseEntity.ok(Map.of("code", 200, "message", "组织标签创建成功", "data", tag));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_CREATE_ORG_TAG", adminUsername, "创建组织标签失败: %s", e, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_CREATE_ORG_TAG", adminUsername, "创建组织标签异常: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "创建组织标签失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取所有组织标签
     */
    @GetMapping("/org-tags")
    public ResponseEntity<?> getAllOrganizationTags(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String cursor) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            List<OrganizationTag> tags = organizationTagRepository.findAll().stream()
                .sorted(Comparator.comparing(OrganizationTag::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "获取组织标签成功",
                "data", PageResult.fromList(tags, PageQuery.of(page, size, cursor))
            ));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_ORG_TAGS", adminUsername, "获取组织标签失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取组织标签失败: " + e.getMessage()));
        }
    }
    
    /**
     * 为用户分配组织标签
     */
    @PutMapping("/users/{userId}/org-tags")
    public ResponseEntity<?> assignOrgTagsToUser(
            @RequestHeader("Authorization") String token,
            @PathVariable Long userId,
            @RequestBody AssignOrgTagsRequest request) {
        
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            userService.assignOrgTagsToUser(userId, request.orgTags(), adminUsername);
            return ResponseEntity.ok(Map.of("code", 200, "message", "组织标签分配成功"));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_ASSIGN_ORG_TAGS", adminUsername, "分配组织标签失败: %s", e, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_ASSIGN_ORG_TAGS", adminUsername, "分配组织标签异常: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "分配组织标签失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取组织标签树结构
     */
    @GetMapping("/org-tags/tree")
    public ResponseEntity<?> getOrganizationTagTree(@RequestHeader("Authorization") String token) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            List<Map<String, Object>> tagTree = userService.getOrganizationTagTree();
            return ResponseEntity.ok(Map.of(
                "code", 200, 
                "message", "获取组织标签树成功", 
                "data", tagTree
            ));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_ORG_TAG_TREE", adminUsername, "获取组织标签树失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取组织标签树失败: " + e.getMessage()));
        }
    }
    
    /**
     * 更新组织标签
     */
    @PutMapping("/org-tags/{tagId}")
    public ResponseEntity<?> updateOrganizationTag(
            @RequestHeader("Authorization") String token,
            @PathVariable String tagId,
            @RequestBody OrgTagUpdateRequest request) {
        
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            OrganizationTag updatedTag = userService.updateOrganizationTag(
                tagId, 
                request.name(), 
                request.description(), 
                request.parentTag(), 
                adminUsername
            );
            return ResponseEntity.ok(Map.of(
                "code", 200, 
                "message", "组织标签更新成功", 
                "data", updatedTag
            ));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_UPDATE_ORG_TAG", adminUsername, "更新组织标签失败: %s", e, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_UPDATE_ORG_TAG", adminUsername, "更新组织标签异常: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "更新组织标签失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除组织标签
     */
    @DeleteMapping("/org-tags/{tagId}")
    public ResponseEntity<?> deleteOrganizationTag(
            @RequestHeader("Authorization") String token,
            @PathVariable String tagId) {
        
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            Map<String, Object> result = userService.deleteOrganizationTag(tagId, adminUsername);
            int affectedUsers = (int) result.getOrDefault("affectedUserCount", 0);
            int affectedDocs = (int) result.getOrDefault("affectedDocumentCount", 0);
            int reassignedChildren = (int) result.getOrDefault("reassignedChildrenCount", 0);
            
            String message = "组织标签删除成功";
            if (affectedUsers > 0 || affectedDocs > 0 || reassignedChildren > 0) {
                message = String.format("组织标签删除成功（已自动处理：%d个子标签重新分配、%d个用户移除标签、%d个文档重新归属）", 
                    reassignedChildren, affectedUsers, affectedDocs);
            }
            
            return ResponseEntity.ok(Map.of(
                "code", 200, 
                "message", message,
                "data", result
            ));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_DELETE_ORG_TAG", adminUsername, "删除组织标签失败: %s", e, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_DELETE_ORG_TAG", adminUsername, "删除组织标签异常: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "删除组织标签失败: " + e.getMessage()));
        }
    }

    /**
     * 获取指定组织标签的所有翻译
     */
    @GetMapping("/org-tags/{tagId}/i18n")
    public ResponseEntity<?> getOrgTagI18n(
            @RequestHeader("Authorization") String token,
            @PathVariable String tagId) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        try {
            return ResponseEntity.ok(Map.of("code", 200, "message", "ok", "data", userService.getOrganizationTagI18n(tagId)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取翻译失败: " + e.getMessage()));
        }
    }

    /**
     * 保存或更新组织标签的某一语言翻译
     */
    @PutMapping("/org-tags/{tagId}/i18n")
    public ResponseEntity<?> upsertOrgTagI18n(
            @RequestHeader("Authorization") String token,
            @PathVariable String tagId,
            @RequestBody OrgTagI18nRequest request) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        try {
            var saved = userService.upsertOrganizationTagI18n(tagId, request.lang(), request.name(), request.description());
            return ResponseEntity.ok(Map.of("code", 200, "message", "翻译已保存", "data", saved));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "保存翻译失败: " + e.getMessage()));
        }
    }

    /**
     * 获取用户列表
     */
    @GetMapping("/users/list")
    public ResponseEntity<?> getUserList(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String orgTag,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor) {
        
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            Map<String, Object> usersData = userService.getUserList(keyword, orgTag, status, page, size, cursor);
            return ResponseEntity.ok(Map.of(
                "code", 200, 
                "message", "获取用户列表成功", 
                "data", usersData
            ));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_GET_USER_LIST", adminUsername, "获取用户列表失败: %s", e, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_USER_LIST", adminUsername, "获取用户列表异常: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取用户列表失败: " + e.getMessage()));
        }
    }
    
    /**
     * 管理员查询所有对话历史
     */
    @GetMapping("/conversation")
    public ResponseEntity<?> getAllConversations(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String userid,
            @RequestParam(required = false) String start_date,
            @RequestParam(required = false) String end_date) {
        
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("ADMIN_GET_ALL_CONVERSATIONS");
        String adminUsername = null;
        try {
            // 验证管理员权限
            adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            validateAdmin(adminUsername);
            
            LogUtils.logBusiness("ADMIN_GET_ALL_CONVERSATIONS", adminUsername, "管理员开始查询对话历史，目标用户ID: %s, 时间范围: %s 到 %s", userid, start_date, end_date);
            
            List<Map<String, Object>> allConversations = new ArrayList<>();
            
            // 如果指定了userid，先验证用户是否存在
            String targetUsername = null;
            if (userid != null && !userid.isEmpty()) {
                try {
                    Long userIdLong = Long.parseLong(userid);
                    Optional<User> targetUser = userRepository.findById(userIdLong);
                    if (targetUser.isPresent()) {
                        targetUsername = targetUser.get().getUsername();
                        LogUtils.logBusiness("ADMIN_GET_ALL_CONVERSATIONS", adminUsername, "找到目标用户: ID=%s, 用户名=%s", userid, targetUsername);
                    } else {
                        LogUtils.logBusiness("ADMIN_GET_ALL_CONVERSATIONS", adminUsername, "目标用户ID不存在: %s", userid);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(Map.of("code", 404, "message", "目标用户不存在"));
                    }
                } catch (NumberFormatException e) {
                    LogUtils.logBusiness("ADMIN_GET_ALL_CONVERSATIONS", adminUsername, "无效的用户ID格式: %s", userid);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("code", 400, "message", "无效的用户ID格式"));
                }
            }
            
            // 获取所有Redis键中以"user:"开头的键
            Set<String> userKeys = redisTemplate.keys("user:*:current_conversation");
            
            if (!userKeys.isEmpty()) {
                for (String userKey : userKeys) {
                    String conversationId = redisTemplate.opsForValue().get(userKey);
                    if (conversationId != null) {
                        // 提取用户ID
                        String redisUserId = userKey.replace("user:", "").replace(":current_conversation", "");
                        
                        // 如果指定了userid，只查询该用户的对话
                        if (userid != null && !userid.isEmpty()) {
                            // 检查Redis中的用户ID是否匹配（可能是数字ID或用户名）
                            if (!redisUserId.equals(userid) && !redisUserId.equals(targetUsername)) {
                                continue;
                            }
                        }
                        
                        // 获取对话内容，使用实际的用户名而不是Redis中的ID
                        String conversationKey = "conversation:" + conversationId;
                        String json = redisTemplate.opsForValue().get(conversationKey);
                        if (json != null) {
                            String displayUsername = targetUsername != null ? targetUsername : redisUserId;
                            processRedisConversation(json, allConversations, displayUsername, start_date, end_date);
                        }
                    }
                }
            }
            
            LogUtils.logBusiness("ADMIN_GET_ALL_CONVERSATIONS", adminUsername, "管理员查询完成，共获取到 %d 条对话记录", allConversations.size());
            LogUtils.logUserOperation(adminUsername, "ADMIN_GET_ALL_CONVERSATIONS", "conversation_history", "SUCCESS");
            monitor.end("管理员查询对话历史成功");
            
            // 构建统一响应格式
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "获取对话历史成功");  
            response.put("data", allConversations);
            return ResponseEntity.ok().body(response);
            
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_GET_ALL_CONVERSATIONS", adminUsername, "管理员获取对话历史失败: %s", e, e.getMessage());
            monitor.end("管理员获取对话历史失败: " + e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_ALL_CONVERSATIONS", adminUsername, "管理员获取对话历史异常: %s", e, e.getMessage());
            monitor.end("管理员获取对话历史异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "服务器内部错误: " + e.getMessage()));
        }
    }
    
    /**
     * 处理Redis中的对话数据
     */
    private void processRedisConversation(String json, List<Map<String, Object>> targetList, String username, String startDate, String endDate) throws JsonProcessingException {
        List<Map<String, String>> history = objectMapper.readValue(json, new TypeReference<>() {});
        
        // 解析时间范围
        java.time.LocalDateTime startDateTime = null;
        java.time.LocalDateTime endDateTime = null;
        
        if (startDate != null && !startDate.trim().isEmpty()) {
            try {
                startDateTime = parseDateTime(startDate);
            } catch (Exception e) {
                LogUtils.logBusinessError("ADMIN_GET_ALL_CONVERSATIONS", username, "起始时间解析失败: %s", e, startDate);
            }
        }
        
        if (endDate != null && !endDate.trim().isEmpty()) {
            try {
                endDateTime = parseDateTime(endDate);
            } catch (Exception e) {
                LogUtils.logBusinessError("ADMIN_GET_ALL_CONVERSATIONS", username, "结束时间解析失败: %s", e, endDate);
            }
        }
        
        // 将对话转换为前端需要的格式，使用存储的时间戳并添加用户名
        for (Map<String, String> message : history) {
            String messageTimestamp = message.getOrDefault("timestamp", "未知时间");
            
            // 时间过滤
            if (startDateTime != null || endDateTime != null) {
                if (!"未知时间".equals(messageTimestamp)) {
                    try {
                        java.time.LocalDateTime messageDateTime = java.time.LocalDateTime.parse(messageTimestamp, 
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
                        
                        // 检查是否在时间范围内
                        if (startDateTime != null && messageDateTime.isBefore(startDateTime)) {
                            continue; // 跳过早于起始时间的消息
                        }
                        if (endDateTime != null && messageDateTime.isAfter(endDateTime)) {
                            continue; // 跳过晚于结束时间的消息
                        }
                    } catch (Exception e) {
                        // 时间戳格式不正确，跳过过滤（包含所有消息）
                        LogUtils.logBusinessError("ADMIN_GET_ALL_CONVERSATIONS", username, "消息时间戳格式错误: %s", e, messageTimestamp);
                    }
                }
                // 如果是"未知时间"且设置了时间过滤，跳过该消息
                else {
                    continue;
                }
            }
            
            Map<String, Object> messageWithMetadata = new HashMap<>();
            messageWithMetadata.put("role", message.get("role"));
            messageWithMetadata.put("content", message.get("content"));
            messageWithMetadata.put("timestamp", messageTimestamp);
            messageWithMetadata.put("username", username);
            targetList.add(messageWithMetadata);
        }
    }
    
    /**
     * 解析日期时间字符串，支持多种格式
     */
    private java.time.LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return null;
        }
        
        try {
            // 尝试标准格式解析 (2023-01-01T12:00:00)
            return java.time.LocalDateTime.parse(dateTimeStr);
        } catch (java.time.format.DateTimeParseException e1) {
            try {
                // 尝试解析不带秒的格式 (2023-01-01T12:00)
                if (dateTimeStr.length() == 16) {
                    return java.time.LocalDateTime.parse(dateTimeStr + ":00");
                }
                
                // 尝试解析不带分钟和秒的格式 (2023-01-01T12)
                if (dateTimeStr.length() == 13) {
                    return java.time.LocalDateTime.parse(dateTimeStr + ":00:00");
                }
                
                // 尝试解析日期格式 (2023-01-01)
                if (dateTimeStr.length() == 10) {
                    return java.time.LocalDateTime.parse(dateTimeStr + "T00:00:00");
                }
                
                // 如果以上都失败，尝试使用自定义格式解析
                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
                return java.time.LocalDateTime.parse(dateTimeStr, formatter);
            } catch (Exception e2) {
                LogUtils.logBusinessError("PARSE_DATETIME", "system", "无法解析日期时间: %s", e2, dateTimeStr);
                throw new CustomException("无效的日期格式: " + dateTimeStr, HttpStatus.BAD_REQUEST);
            }
        }
    }
    
    // ========== 角色管理 API ==========

    /**
     * 获取所有角色列表
     */
    @GetMapping("/roles")
    public ResponseEntity<?> getAllRoles(@RequestHeader("Authorization") String token) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        try {
            List<Map<String, Object>> roles = roleRepository.findAll().stream()
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", r.getId());
                    m.put("roleCode", r.getRoleCode());
                    m.put("roleName", r.getRoleName());
                    m.put("description", r.getDescription());
                    m.put("isSystem", r.isSystem());
                    m.put("permissions", r.getPermissions().stream()
                        .map(p -> Map.of("permCode", p.getPermCode(), "permName", p.getPermName()))
                        .toList());
                    return m;
                }).toList();
            return ResponseEntity.ok(Map.of("code", 200, "message", "获取角色列表成功", "data", roles));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_ALL_ROLES", adminUsername, "获取角色列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取角色列表失败: " + e.getMessage()));
        }
    }

    /**
     * 获取所有权限列表（用于角色编辑时选择）
     */
    @GetMapping("/permissions")
    public ResponseEntity<?> getAllPermissions(@RequestHeader("Authorization") String token) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        try {
            List<Map<String, Object>> perms = permissionRepository.findAll().stream()
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", p.getId());
                    m.put("permCode", p.getPermCode());
                    m.put("permName", p.getPermName());
                    m.put("resourceType", p.getResourceType());
                    m.put("action", p.getAction());
                    m.put("description", p.getDescription());
                    return m;
                }).toList();
            return ResponseEntity.ok(Map.of("code", 200, "message", "获取权限列表成功", "data", perms));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_ALL_PERMISSIONS", adminUsername, "获取权限列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取权限列表失败: " + e.getMessage()));
        }
    }

    /**
     * 创建角色
     */
    @PostMapping("/roles")
    public ResponseEntity<?> createRole(
            @RequestHeader("Authorization") String token,
            @RequestBody CreateRoleRequest request) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        try {
            if (roleRepository.existsByRoleCode(request.roleCode())) {
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "角色编码已存在: " + request.roleCode()));
            }
            Role role = new Role();
            role.setRoleCode(request.roleCode());
            role.setRoleName(request.roleName());
            role.setDescription(request.description());
            role.setSystem(false);
            roleRepository.save(role);
            LogUtils.logUserOperation(adminUsername, "CREATE_ROLE", "role:" + request.roleCode(), "SUCCESS");
            return ResponseEntity.ok(Map.of("code", 200, "message", "角色创建成功"));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_CREATE_ROLE", adminUsername, "创建角色失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "创建角色失败: " + e.getMessage()));
        }
    }

    /**
     * 更新角色基本信息及其权限列表
     */
    @PutMapping("/roles/{roleId}")
    public ResponseEntity<?> updateRole(
            @RequestHeader("Authorization") String token,
            @PathVariable Long roleId,
            @RequestBody UpdateRoleRequest request) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        try {
            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new CustomException("角色不存在", HttpStatus.NOT_FOUND));
            if (request.roleName() != null) role.setRoleName(request.roleName());
            if (request.description() != null) role.setDescription(request.description());
            if (request.permCodes() != null) {
                Set<Permission> newPerms = new HashSet<>();
                for (String permCode : request.permCodes()) {
                    Permission perm = permissionRepository.findByPermCode(permCode)
                            .orElseThrow(() -> new CustomException("权限不存在: " + permCode, HttpStatus.BAD_REQUEST));
                    newPerms.add(perm);
                }
                role.setPermissions(newPerms);
            }
            roleRepository.save(role);
            // 清除所有拥有该角色的用户的权限缓存
            List<User> affectedUsers = userRepository.findAll().stream()
                .filter(u -> u.getRoles().stream().anyMatch(r -> r.getId().equals(roleId)))
                .toList();
            affectedUsers.forEach(u -> rbacService.evictUserPermissionCache(u.getUsername()));
            LogUtils.logUserOperation(adminUsername, "UPDATE_ROLE", "role:" + roleId, "SUCCESS");
            return ResponseEntity.ok(Map.of("code", 200, "message", "角色更新成功"));
        } catch (CustomException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_UPDATE_ROLE", adminUsername, "更新角色失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "更新角色失败: " + e.getMessage()));
        }
    }

    /**
     * 删除角色（内置角色不允许删除）
     */
    @DeleteMapping("/roles/{roleId}")
    public ResponseEntity<?> deleteRole(
            @RequestHeader("Authorization") String token,
            @PathVariable Long roleId) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        try {
            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new CustomException("角色不存在", HttpStatus.NOT_FOUND));
            if (role.isSystem()) {
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "内置角色不允许删除"));
            }
            // 清除所有拥有该角色用户的权限缓存
            List<User> affectedUsers = userRepository.findAll().stream()
                .filter(u -> u.getRoles().stream().anyMatch(r -> r.getId().equals(roleId)))
                .toList();
            affectedUsers.forEach(u -> rbacService.evictUserPermissionCache(u.getUsername()));
            roleRepository.delete(role);
            LogUtils.logUserOperation(adminUsername, "DELETE_ROLE", "role:" + roleId, "SUCCESS");
            return ResponseEntity.ok(Map.of("code", 200, "message", "角色删除成功"));
        } catch (CustomException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_DELETE_ROLE", adminUsername, "删除角色失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "删除角色失败: " + e.getMessage()));
        }
    }

    /**
     * 获取指定用户的角色列表
     */
    @GetMapping("/users/{userId}/roles")
    public ResponseEntity<?> getUserRoles(
            @RequestHeader("Authorization") String token,
            @PathVariable Long userId) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CustomException("用户不存在", HttpStatus.NOT_FOUND));
            List<Map<String, Object>> roles = user.getRoles().stream()
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", r.getId());
                    m.put("roleCode", r.getRoleCode());
                    m.put("roleName", r.getRoleName());
                    m.put("description", r.getDescription());
                    m.put("isSystem", r.isSystem());
                    return m;
                }).toList();
            return ResponseEntity.ok(Map.of("code", 200, "message", "获取用户角色成功", "data", roles));
        } catch (CustomException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_USER_ROLES", adminUsername, "获取用户角色失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取用户角色失败: " + e.getMessage()));
        }
    }

    /**
     * 为用户分配角色（全量替换）
     */
    @PutMapping("/users/{userId}/roles")
    public ResponseEntity<?> assignRolesToUser(
            @RequestHeader("Authorization") String token,
            @PathVariable Long userId,
            @RequestBody AssignRolesRequest request) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CustomException("用户不存在", HttpStatus.NOT_FOUND));

            // 查找所有目标角色
            Set<Role> newRoles = new HashSet<>();
            for (String roleCode : request.roleCodes()) {
                Role role = roleRepository.findByRoleCode(roleCode)
                        .orElseThrow(() -> new CustomException("角色不存在: " + roleCode, HttpStatus.BAD_REQUEST));
                newRoles.add(role);
            }

            user.setRoles(newRoles);
            // 同步 legacyRole
            boolean isAdmin = newRoles.stream().anyMatch(r -> "ROLE_ADMIN".equals(r.getRoleCode()));
            user.setRole(isAdmin ? User.Role.ADMIN : User.Role.USER);

            userRepository.save(user);

            // 清除权限缓存
            rbacService.evictUserPermissionCache(user.getUsername());

            LogUtils.logUserOperation(adminUsername, "ASSIGN_ROLES",
                "user:" + userId, "SUCCESS");
            return ResponseEntity.ok(Map.of("code", 200, "message", "角色分配成功"));
        } catch (CustomException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_ASSIGN_ROLES", adminUsername, "分配角色失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "分配角色失败: " + e.getMessage()));
        }
    }

    /**
     * 验证用户是否为管理员
     */
    private void validateAdmin(String username) {
        if (username == null || username.isEmpty()) {
            throw new CustomException("Invalid token", HttpStatus.UNAUTHORIZED);
        }
        
        User admin = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        
        if (admin.getRole() != User.Role.ADMIN) {
            throw new CustomException("Unauthorized access: Admin role required", HttpStatus.FORBIDDEN);
        }
    }

    /**
     * 迁移 MinIO 文件从旧路径到新路径
     * 旧路径: merged/{fileName}
     * 新路径: merged/{fileMd5}
     *
     * @param token JWT token
     * @param adminKey 管理员密钥（简单验证）
     * @return 迁移报告
     */
    @PostMapping("/migrate-minio")
    public ResponseEntity<?> migrateMinioFiles(
            @RequestHeader("Authorization") String token,
            @RequestParam String adminKey) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("MIGRATE_MINIO");
        String adminUsername = null;

        try {
            // 验证管理员权限
            adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            validateAdmin(adminUsername);

            // 简单密钥验证
            if (!"migration2024".equals(adminKey)) {
                Map<String, Object> response = new HashMap<>();
                response.put("code", 403);
                response.put("message", "无效的管理员密钥");
                return ResponseEntity.status(403).body(response);
            }

            LogUtils.logBusiness("MIGRATE_MINIO", adminUsername, "开始MinIO文件迁移");

            MinioMigrationUtil.MigrationReport report = migrationUtil.migrateAllFiles();

            LogUtils.logBusiness("MIGRATE_MINIO", adminUsername,
                "迁移完成: 成功=%d, 跳过=%d, 失败=%d",
                report.successCount, report.skipCount, report.errorCount);

            monitor.end("MinIO文件迁移完成");

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "迁移完成");
            response.put("data", Map.of(
                "successCount", report.successCount,
                "skipCount", report.skipCount,
                "errorCount", report.errorCount,
                "errors", report.getErrors()
            ));
            return ResponseEntity.ok(response);

        } catch (CustomException e) {
            LogUtils.logBusinessError("MIGRATE_MINIO", adminUsername, "MinIO文件迁移失败: %s", e, e.getMessage());
            monitor.end("MinIO文件迁移失败: " + e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("code", e.getStatus().value());
            response.put("message", e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(response);
        } catch (Exception e) {
            LogUtils.logBusinessError("MIGRATE_MINIO", adminUsername, "MinIO文件迁移异常: %s", e, e.getMessage());
            monitor.end("MinIO文件迁移失败: " + e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("code", 500);
            response.put("message", "迁移失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 清空所有数据（危险操作，仅用于测试环境）
     *
     * @param token JWT token
     * @param adminKey 管理员密钥
     * @return 操作结果
     */
    @PostMapping("/clear-all-data")
    public ResponseEntity<?> clearAllData(
            @RequestHeader("Authorization") String token,
            @RequestParam String adminKey) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("CLEAR_ALL_DATA");
        String adminUsername = null;

        try {
            // 验证管理员权限
            adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            validateAdmin(adminUsername);

            // 更严格的密钥验证
            if (!"CLEAR_ALL_2024".equals(adminKey)) {
                Map<String, Object> response = new HashMap<>();
                response.put("code", 403);
                response.put("message", "无效的管理员密钥");
                return ResponseEntity.status(403).body(response);
            }

            LogUtils.logBusiness("CLEAR_ALL_DATA", adminUsername, "开始清空所有数据");

            migrationUtil.clearAllData();

            LogUtils.logBusiness("CLEAR_ALL_DATA", adminUsername, "所有数据已清空");

            monitor.end("数据清空完成");

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "所有数据已清空");
            return ResponseEntity.ok(response);

        } catch (CustomException e) {
            LogUtils.logBusinessError("CLEAR_ALL_DATA", adminUsername, "清空数据失败: %s", e, e.getMessage());
            monitor.end("数据清空失败: " + e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("code", e.getStatus().value());
            response.put("message", e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(response);
        } catch (Exception e) {
            LogUtils.logBusinessError("CLEAR_ALL_DATA", adminUsername, "清空数据异常: %s", e, e.getMessage());
            monitor.end("数据清空失败: " + e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("code", 500);
            response.put("message", "清空失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /** 分配角色请求体 */
    public record AssignRolesRequest(List<String> roleCodes) {}

    /** 创建角色请求体 */
    public record CreateRoleRequest(String roleCode, String roleName, String description) {}

    /** 更新角色请求体（权限列表全量替换） */
    public record UpdateRoleRequest(String roleName, String description, List<String> permCodes) {}

    /** 管理员用户请求体 */
    public record AdminUserRequest(String username, String password) {}

    /** 组织标签请求体 */
    public record OrgTagRequest(String tagId, String name, String description, String parentTag) {}

    /** 分配组织标签请求体 */
    public record AssignOrgTagsRequest(List<String> orgTags) {}

    /** 组织标签更新请求记录类 */
    public record OrgTagUpdateRequest(String name, String description, String parentTag) {}

    /** 组织标签 i18n 写入请求体 */
    public record OrgTagI18nRequest(String lang, String name, String description) {}

    private long getRedisLong(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value == null ? 0L : Long.parseLong(value);
        } catch (Exception e) {
            return 0L;
        }
    }

    private List<Map<String, Object>> buildUsageTrends(LocalDate today) {
        List<Map<String, Object>> trends = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.plusDays(1).atStartOfDay();
            long dbQuestions = conversationRepository.findByTimestampBetween(start, end).size();
            long redisQuestions = getRedisLong("analytics:qa:" + date);
            long questions = Math.max(dbQuestions, redisQuestions);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date.toString());
            item.put("label", date.getMonthValue() + "/" + date.getDayOfMonth());
            item.put("questions", questions);
            trends.add(item);
        }
        return trends;
    }

    private List<Map<String, Object>> buildPopularQuestions(LocalDate today) {
        List<Map<String, Object>> questions = new ArrayList<>();
        try {
            Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores("analytics:popular_questions:" + today, 0, 4);
            if (tuples != null) {
                int rank = 1;
                for (org.springframework.data.redis.core.ZSetOperations.TypedTuple<String> tuple : tuples) {
                    if (tuple.getValue() == null) {
                        continue;
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("rank", rank++);
                    item.put("question", tuple.getValue());
                    item.put("count", tuple.getScore() == null ? 0 : tuple.getScore().longValue());
                    questions.add(item);
                }
            }
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_SYSTEM_STATS", "system", "统计热门问题失败", e);
        }
        return questions;
    }

    /**
     * Trigger background translation of all KB / Agent / OrgTag entries that
     * have no i18n record yet.  Returns immediately; translation happens async.
     */
    @PostMapping("/i18n/sync")
    @PreAuthorize("hasAuthority('system:admin')")
    public ResponseEntity<?> syncI18n() {
        i18nTranslationService.syncAllI18n();
        return ResponseEntity.ok(Map.of("code", 200, "message", "i18n sync started in background"));
    }
}
