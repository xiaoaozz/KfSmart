package com.smart.kf.controller.admin;

import com.smart.kf.config.KafkaConfig;
import com.smart.kf.model.FileProcessingTask;
import com.smart.kf.model.FileUpload;
import com.smart.kf.repository.FileUploadRepository;
import com.smart.kf.repository.UserRepository;
import com.smart.kf.service.AdminAuthHelper;
import com.smart.kf.service.DocumentService;
import com.smart.kf.service.FileTypeValidationService;
import com.smart.kf.utils.JwtUtils;
import com.smart.kf.utils.LogUtils;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Optional;

/**
 * 管理员知识库文档管理控制器（从 AdminController 拆分而来）。
 * 全部接口需要 system:admin 权限（在 SecurityConfig 路由级保护，此处方法级双重保障）
 */
@RestController
@RequestMapping("/api/v1/admin/knowledge")
@PreAuthorize("hasAuthority('system:admin')")
@RequiredArgsConstructor
public class AdminKnowledgeController {

    private final DocumentService documentService;
    private final FileUploadRepository fileUploadRepository;
    private final MinioClient minioClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaConfig kafkaConfig;
    private final FileTypeValidationService fileTypeValidationService;
    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final AdminAuthHelper adminAuthHelper;

    @Qualifier("minioPublicUrl")
    private final String minioPublicUrl;

    /**
     * 添加知识库文档
     */
    @PostMapping("/add")
    public ResponseEntity<?> addKnowledgeDocument(
            @RequestHeader("Authorization") String token,
            @RequestParam("file") MultipartFile file,
            @RequestParam("description") String description) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("ADMIN_ADD_KNOWLEDGE");
        String adminUsername = null;
        try {
            adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            adminAuthHelper.validateAdmin(adminUsername);

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
            // 同时写入 ownerId FK
            userRepository.findByUsername(adminUsername).ifPresent(u -> fileUpload.setOwnerId(u.getId()));
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
    @DeleteMapping("/{documentId}")
    public ResponseEntity<?> deleteKnowledgeDocument(
            @RequestHeader("Authorization") String token,
            @PathVariable("documentId") String documentId) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("ADMIN_DELETE_KNOWLEDGE");
        String adminUsername = null;
        try {
            adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            adminAuthHelper.validateAdmin(adminUsername);

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
}
