package com.smart.kf.controller;

import com.smart.kf.exception.CustomException;
import com.smart.kf.model.FileUpload;
import com.smart.kf.model.KnowledgeBase;
import com.smart.kf.model.User;
import com.smart.kf.repository.ConversationRepository;
import com.smart.kf.repository.FileUploadRepository;
import com.smart.kf.repository.KnowledgeBaseRepository;
import com.smart.kf.repository.UserRepository;
import com.smart.kf.service.UserService;
import com.smart.kf.utils.JwtUtils;
import com.smart.kf.utils.LogUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024;
    private static final Set<String> ALLOWED_AVATAR_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private FileUploadRepository fileUploadRepository;

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    // 用户注册接口
    // 接收用户请求体中的用户名和密码，并调用用户服务进行注册
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserRequest request) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("USER_REGISTER");
        try {
            if (request.username() == null || request.username().isEmpty() ||
                    request.password() == null || request.password().isEmpty()) {
                LogUtils.logUserOperation("anonymous", "REGISTER", "validation", "FAILED_EMPTY_PARAMS");
                monitor.end("注册失败：参数为空");
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "用户名和密码不能为空"));
            }
            
            userService.registerUser(request.username(), request.password());
            LogUtils.logUserOperation(request.username(), "REGISTER", "user_creation", "SUCCESS");
            monitor.end("注册成功");
            
            return ResponseEntity.ok(Map.of("code", 200, "message", "User registered successfully"));
        } catch (CustomException e) {
            LogUtils.logBusinessError("USER_REGISTER", request.username(), "用户注册失败: %s", e, e.getMessage());
            monitor.end("注册失败: " + e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("USER_REGISTER", request.username(), "用户注册异常: %s", e, e.getMessage());
            monitor.end("注册异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    // 用户登录接口
    // 验证用户身份并生成JWT令牌
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserRequest request, HttpServletRequest httpRequest) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("USER_LOGIN");
        String clientIp = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        try {
            if (request.username() == null || request.username().isEmpty() ||
                    request.password() == null || request.password().isEmpty()) {
                LogUtils.logUserOperation("anonymous", "LOGIN", "validation", "FAILED_EMPTY_PARAMS");
                // 记录登录失败
                try {
                    userService.recordLogin(request.username(), null, clientIp, userAgent, "FAILED", "用户名或密码为空");
                } catch (Exception ignored) {}
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "用户名和密码不能为空"));
            }
            
            String username = userService.authenticateUser(request.username(), request.password());
            if (username == null) {
                LogUtils.logUserOperation(request.username(), "LOGIN", "authentication", "FAILED_INVALID_CREDENTIALS");
                // 记录登录失败
                try {
                    userService.recordLogin(request.username(), null, clientIp, userAgent, "FAILED", "用户名或密码错误");
                } catch (Exception ignored) {}
                return ResponseEntity.status(401).body(Map.of("code", 401, "message", "用户名或密码错误"));
            }
            
            // 记录登录成功
            User loginUser = userRepository.findByUsername(username).orElse(null);
            if (loginUser != null) {
                try {
                    userService.recordLogin(username, loginUser.getId(), clientIp, userAgent, "SUCCESS", null);
                } catch (Exception ignored) {}
            }

            String token = jwtUtils.generateToken(username);
            String refreshToken = jwtUtils.generateRefreshToken(username);
            LogUtils.logUserOperation(username, "LOGIN", "token_generation", "SUCCESS");
            monitor.end("登录成功");
            
            return ResponseEntity.ok(Map.of("code", 200, "message", "登录成功", "data", Map.of(
                "token", token,
                "refreshToken", refreshToken
            )));
        } catch (CustomException e) {
            LogUtils.logBusinessError("USER_LOGIN", request.username(), "登录失败: %s", e, e.getMessage());
            // 记录登录失败
            try {
                userService.recordLogin(request.username(), null, clientIp, userAgent, "FAILED", e.getMessage());
            } catch (Exception ignored) {}
            monitor.end("登录失败: " + e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("USER_LOGIN", request.username(), "登录异常: %s", e, e.getMessage());
            // 记录登录失败
            try {
                userService.recordLogin(request.username(), null, clientIp, userAgent, "FAILED", "系统异常: " + e.getMessage());
            } catch (Exception ignored) {}
            monitor.end("登录异常: " + e.getMessage());
            return ResponseEntity.status(500).body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    // 获取当前用户信息
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader("Authorization") String token) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("GET_USER_INFO");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            if (username == null || username.isEmpty()) {
                LogUtils.logUserOperation("anonymous", "GET_USER_INFO", "token_validation", "FAILED_INVALID_TOKEN");
                monitor.end("获取用户信息失败：无效token");
                throw new CustomException("Invalid token", HttpStatus.UNAUTHORIZED);
            }

            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

            // 手动构建返回对象，不包含 password 字段
            Map<String, Object> displayUserData = new LinkedHashMap<>();
            displayUserData.put("id", user.getId());
            displayUserData.put("username", user.getUsername());
            displayUserData.put("role", user.getRole());
            
            // 添加组织标签信息
            if (user.getOrgTags() != null && !user.getOrgTags().isEmpty()) {
                List<String> orgTagsList = Arrays.asList(user.getOrgTags().split(","));
                displayUserData.put("orgTags", orgTagsList);
            } else {
                displayUserData.put("orgTags", List.of());
            }
            
            // 添加主组织标签信息
            displayUserData.put("primaryOrg", user.getPrimaryOrg());
            displayUserData.put("avatar", user.getAvatarUrl());
            
            displayUserData.put("createdAt", user.getCreatedAt());
            displayUserData.put("updatedAt", user.getUpdatedAt());

            LogUtils.logUserOperation(username, "GET_USER_INFO", "user_profile", "SUCCESS");
            monitor.end("获取用户信息成功");

            // 返回响应
            return ResponseEntity.ok(Map.of("code", 200, "message", "Get user detail successful", "data", displayUserData));
        } catch (CustomException e) {
            LogUtils.logBusinessError("GET_USER_INFO", username, "获取用户信息失败: %s", e, e.getMessage());
            monitor.end("获取用户信息失败: " + e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_USER_INFO", username, "获取用户信息异常: %s", e, e.getMessage());
            monitor.end("获取用户信息异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    // 更新当前用户头像
    @PostMapping("/me/avatar")
    public ResponseEntity<?> updateCurrentUserAvatar(
            @RequestHeader("Authorization") String token,
            @RequestParam("file") MultipartFile file) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("UPDATE_USER_AVATAR");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            if (username == null || username.isEmpty()) {
                LogUtils.logUserOperation("anonymous", "UPDATE_AVATAR", "token_validation", "FAILED_INVALID_TOKEN");
                monitor.end("更新头像失败：无效token");
                throw new CustomException("Invalid token", HttpStatus.UNAUTHORIZED);
            }

            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

            validateAvatarFile(file);

            String extension = getAvatarExtension(file.getContentType());
            Path avatarDir = Path.of("data", "avatars");
            Files.createDirectories(avatarDir);
            String fileName = "user-" + user.getId() + extension;
            Path target = avatarDir.resolve(fileName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            String avatarUrl = "/avatars/" + fileName;
            user.setAvatarUrl(avatarUrl);
            userRepository.save(user);

            LogUtils.logUserOperation(username, "UPDATE_AVATAR", avatarUrl, "SUCCESS");
            monitor.end("更新头像成功");

            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "Avatar updated successfully",
                "data", Map.of("avatar", avatarUrl)
            ));
        } catch (CustomException e) {
            LogUtils.logBusinessError("UPDATE_USER_AVATAR", username, "更新头像失败: %s", e, e.getMessage());
            monitor.end("更新头像失败: " + e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (IOException e) {
            LogUtils.logBusinessError("UPDATE_USER_AVATAR", username, "保存头像失败: %s", e, e.getMessage());
            monitor.end("保存头像失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "头像保存失败"));
        } catch (Exception e) {
            LogUtils.logBusinessError("UPDATE_USER_AVATAR", username, "更新头像异常: %s", e, e.getMessage());
            monitor.end("更新头像异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    /**
     * 获取当前用户的个人使用统计。
     */
    @GetMapping("/usage-stats")
    public ResponseEntity<?> getUsageStatistics(
            @RequestHeader("Authorization") String token,
            @RequestParam(defaultValue = "7") int days) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("GET_USAGE_STATS");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            if (username == null || username.isEmpty()) {
                monitor.end("获取使用统计失败：无效token");
                throw new CustomException("Invalid token", HttpStatus.UNAUTHORIZED);
            }

            int normalizedDays = days == 30 ? 30 : 7;
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

            Map<String, Object> stats = buildUsageStatistics(user, normalizedDays);
            LogUtils.logUserOperation(username, "GET_USAGE_STATS", "query", "SUCCESS");
            monitor.end("获取使用统计成功");

            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "Get usage statistics successful",
                "data", stats
            ));
        } catch (CustomException e) {
            LogUtils.logBusinessError("GET_USAGE_STATS", username, "获取使用统计失败: %s", e, e.getMessage());
            monitor.end("获取使用统计失败: " + e.getMessage());
            return ResponseEntity.status(e.getStatus())
                    .body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_USAGE_STATS", username, "获取使用统计异常: %s", e, e.getMessage());
            monitor.end("获取使用统计异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "Internal server error"));
        }
    }
    
    // 获取用户组织标签信息
    @GetMapping("/org-tags")
    public ResponseEntity<?> getUserOrgTags(@RequestHeader("Authorization") String token) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("GET_USER_ORG_TAGS");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            if (username == null || username.isEmpty()) {
                LogUtils.logUserOperation("anonymous", "GET_ORG_TAGS", "token_validation", "FAILED_INVALID_TOKEN");
                monitor.end("获取组织标签失败：无效token");
                throw new CustomException("Invalid token", HttpStatus.UNAUTHORIZED);
            }
            
            Map<String, Object> orgTagsInfo = userService.getUserOrgTags(username);
            
            LogUtils.logUserOperation(username, "GET_ORG_TAGS", "organization_tags", "SUCCESS");
            monitor.end("获取组织标签成功");
            
            return ResponseEntity.ok(Map.of(
                "code", 200, 
                "message", "Get user organization tags successful", 
                "data", orgTagsInfo
            ));
        } catch (CustomException e) {
            LogUtils.logBusinessError("GET_USER_ORG_TAGS", username, "获取用户组织标签失败: %s", e, e.getMessage());
            monitor.end("获取组织标签失败: " + e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_USER_ORG_TAGS", username, "获取用户组织标签异常: %s", e, e.getMessage());
            monitor.end("获取组织标签异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "Internal server error"));
        }
    }
    
    /**
     * 获取组织标签树（用户版本）
     * 普通用户可以访问此接口，用于在创建知识库等场景下选择组织标签
     * 
     * @param token JWT token
     * @return 组织标签树结构
     */
    @GetMapping("/org-tags/tree")
    public ResponseEntity<?> getUserOrgTagTree(@RequestHeader("Authorization") String token) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("GET_USER_ORG_TAG_TREE");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            if (username == null || username.isEmpty()) {
                LogUtils.logUserOperation("anonymous", "GET_ORG_TAG_TREE", "token_validation", "FAILED_INVALID_TOKEN");
                monitor.end("获取组织标签树失败：无效token");
                throw new CustomException("Invalid token", HttpStatus.UNAUTHORIZED);
            }
            
            // 调用service获取组织标签树
            List<Map<String, Object>> tagTree = userService.getOrganizationTagTree();
            
            LogUtils.logUserOperation(username, "GET_ORG_TAG_TREE", "organization_tags_tree", "SUCCESS");
            monitor.end("获取组织标签树成功");
            
            return ResponseEntity.ok(Map.of(
                "code", 200, 
                "message", "获取组织标签树成功", 
                "data", tagTree
            ));
        } catch (CustomException e) {
            LogUtils.logBusinessError("GET_USER_ORG_TAG_TREE", username, "获取组织标签树失败: %s", e, e.getMessage());
            monitor.end("获取组织标签树失败: " + e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_USER_ORG_TAG_TREE", username, "获取组织标签树异常: %s", e, e.getMessage());
            monitor.end("获取组织标签树异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取组织标签树失败: " + e.getMessage()));
        }
    }
    
    // 设置用户主组织标签
    @PutMapping("/primary-org")
    public ResponseEntity<?> setPrimaryOrg(@RequestHeader("Authorization") String token, @RequestBody PrimaryOrgRequest request) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("SET_PRIMARY_ORG");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            if (username == null || username.isEmpty()) {
                LogUtils.logUserOperation("anonymous", "SET_PRIMARY_ORG", "token_validation", "FAILED_INVALID_TOKEN");
                monitor.end("设置主组织失败：无效token");
                throw new CustomException("Invalid token", HttpStatus.UNAUTHORIZED);
            }
            
            if (request.primaryOrg() == null || request.primaryOrg().isEmpty()) {
                LogUtils.logUserOperation(username, "SET_PRIMARY_ORG", "validation", "FAILED_EMPTY_ORG");
                monitor.end("设置主组织失败：组织标签为空");
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "Primary organization tag cannot be empty"));
            }
            
            userService.setUserPrimaryOrg(username, request.primaryOrg());
            
            LogUtils.logUserOperation(username, "SET_PRIMARY_ORG", request.primaryOrg(), "SUCCESS");
            monitor.end("设置主组织成功");
            
            return ResponseEntity.ok(Map.of("code", 200, "message", "Primary organization set successfully"));
        } catch (CustomException e) {
            LogUtils.logBusinessError("SET_PRIMARY_ORG", username, "设置主组织失败: %s", e, e.getMessage());
            monitor.end("设置主组织失败: " + e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("SET_PRIMARY_ORG", username, "设置主组织异常: %s", e, e.getMessage());
            monitor.end("设置主组织异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    // 获取当前用户组织标签信息 (供上传文件时使用)
    @GetMapping("/upload-orgs")
    public ResponseEntity<?> getUploadOrgTags(@RequestAttribute("userId") String userId) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("GET_UPLOAD_ORG_TAGS");
        try {
            LogUtils.logBusiness("GET_UPLOAD_ORG_TAGS", userId, "获取用户上传组织标签信息");
            
            // 获取用户所有组织标签
            List<String> orgTags = Arrays.asList(userService.getUserOrgTags(userId).get("orgTags").toString().split(","));
            // 获取用户主组织标签
            String primaryOrg = userService.getUserPrimaryOrg(userId);
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("orgTags", orgTags);
            responseData.put("primaryOrg", primaryOrg);
            
            LogUtils.logUserOperation(userId, "GET_UPLOAD_ORG_TAGS", "upload_organizations", "SUCCESS");
            monitor.end("获取上传组织标签成功");
            
            return ResponseEntity.ok(Map.of(
                "code", 200, 
                "message", "获取用户上传组织标签成功", 
                "data", responseData
            ));
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_UPLOAD_ORG_TAGS", userId, "获取用户上传组织标签失败: %s", e, e.getMessage());
            monitor.end("获取上传组织标签失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "code", 500, 
                "message", "获取用户上传组织标签失败: " + e.getMessage()
            ));
        }
    }

    // 用户登出接口
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String token) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("USER_LOGOUT");
        String username = null;
        try {
            if (token == null || !token.startsWith("Bearer ")) {
                LogUtils.logUserOperation("anonymous", "LOGOUT", "validation", "FAILED_INVALID_TOKEN");
                monitor.end("登出失败：token格式无效");
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "Invalid token format"));
            }

            String jwtToken = token.replace("Bearer ", "");
            username = jwtUtils.extractUsernameFromToken(jwtToken);
            
            if (username == null || username.isEmpty()) {
                LogUtils.logUserOperation("anonymous", "LOGOUT", "token_extraction", "FAILED_NO_USERNAME");
                monitor.end("登出失败：无法提取用户名");
                return ResponseEntity.status(401).body(Map.of("code", 401, "message", "Invalid token"));
            }

            // 使当前token失效
            jwtUtils.invalidateToken(jwtToken);
            
            LogUtils.logUserOperation(username, "LOGOUT", "token_invalidation", "SUCCESS");
            monitor.end("登出成功");

            return ResponseEntity.ok(Map.of("code", 200, "message", "Logout successful"));
        } catch (Exception e) {
            LogUtils.logBusinessError("USER_LOGOUT", username, "登出异常: %s", e, e.getMessage());
            monitor.end("登出异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    // 用户批量登出接口（登出所有设备）
    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAll(@RequestHeader("Authorization") String token) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("USER_LOGOUT_ALL");
        String username = null;
        try {
            if (token == null || !token.startsWith("Bearer ")) {
                LogUtils.logUserOperation("anonymous", "LOGOUT_ALL", "validation", "FAILED_INVALID_TOKEN");
                monitor.end("批量登出失败：token格式无效");
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "Invalid token format"));
            }

            String jwtToken = token.replace("Bearer ", "");
            username = jwtUtils.extractUsernameFromToken(jwtToken);
            String userId = jwtUtils.extractUserIdFromToken(jwtToken);
            
            if (username == null || username.isEmpty() || userId == null) {
                LogUtils.logUserOperation("anonymous", "LOGOUT_ALL", "token_extraction", "FAILED_NO_USER_INFO");
                monitor.end("批量登出失败：无法提取用户信息");
                return ResponseEntity.status(401).body(Map.of("code", 401, "message", "Invalid token"));
            }

            // 使用户所有token失效
            jwtUtils.invalidateAllUserTokens(userId);
            
            LogUtils.logUserOperation(username, "LOGOUT_ALL", "all_tokens_invalidation", "SUCCESS");
            monitor.end("批量登出成功");

            return ResponseEntity.ok(Map.of("code", 200, "message", "Logout from all devices successful"));
        } catch (Exception e) {
            LogUtils.logBusinessError("USER_LOGOUT_ALL", username, "批量登出异常: %s", e, e.getMessage());
            monitor.end("批量登出异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "Internal server error"));
        }
    }
    
    // 用户请求记录类
    public record UserRequest(String username, String password) {}

    // 主组织标签请求记录类
    public record PrimaryOrgRequest(String primaryOrg) {}

    private void validateAvatarFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException("头像文件不能为空", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new CustomException("头像文件不能超过2MB", HttpStatus.BAD_REQUEST);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_AVATAR_TYPES.contains(contentType.toLowerCase())) {
            throw new CustomException("仅支持 JPG、PNG、WebP、GIF 格式头像", HttpStatus.BAD_REQUEST);
        }
    }

    private String getAvatarExtension(String contentType) {
        if (contentType == null) {
            return ".png";
        }
        return switch (contentType.toLowerCase()) {
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".png";
        };
    }

    private Map<String, Object> buildUsageStatistics(User user, int days) {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();

        List<FileUpload> ownFiles = findOwnFiles(user);
        List<FileUpload> completedFiles = ownFiles.stream()
                .filter(file -> file.getStatus() == 1)
                .toList();
        List<KnowledgeBase> ownKnowledgeBases = knowledgeBaseRepository.findByCreatedBy(user);
        List<Map<String, Object>> usageTrends = buildPersonalUsageTrends(user, today, days);

        long totalConversations = conversationRepository.findByUserId(user.getId()).size();
        long todayConversations = conversationRepository
                .findByUserIdAndTimestampBetween(user.getId(), todayStart, tomorrowStart)
                .size();
        long weekActiveDays = buildPersonalUsageTrends(user, today, 7).stream()
                .filter(item -> ((Number) item.get("questions")).longValue() > 0)
                .count();
        long totalStorage = completedFiles.stream().mapToLong(FileUpload::getTotalSize).sum();
        long todayUploads = completedFiles.stream()
                .filter(file -> file.getCreatedAt() != null && !file.getCreatedAt().isBefore(todayStart))
                .count();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalConversations", totalConversations);
        stats.put("todayConversations", todayConversations);
        stats.put("totalDocuments", completedFiles.size());
        stats.put("todayUploads", todayUploads);
        stats.put("knowledgeBaseCount", ownKnowledgeBases.size());
        stats.put("weekActiveDays", weekActiveDays);
        stats.put("totalStorage", totalStorage);
        stats.put("favoriteCount", 0);
        stats.put("usageTrends", usageTrends);
        stats.put("topKnowledgeBases", buildTopKnowledgeBases(ownKnowledgeBases, completedFiles));
        stats.put("featureUsage", buildFeatureUsage(totalConversations, completedFiles.size(), ownKnowledgeBases.size()));
        stats.put("rangeDays", days);
        return stats;
    }

    private List<FileUpload> findOwnFiles(User user) {
        Set<Long> seenIds = new HashSet<>();
        List<FileUpload> files = new ArrayList<>();
        for (String ownerId : List.of(user.getId().toString(), user.getUsername())) {
            for (FileUpload file : fileUploadRepository.findByUserId(ownerId)) {
                Long id = file.getId();
                if (id == null || seenIds.add(id)) {
                    files.add(file);
                }
            }
        }
        return files;
    }

    private List<Map<String, Object>> buildPersonalUsageTrends(User user, LocalDate today, int days) {
        List<Map<String, Object>> trends = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.plusDays(1).atStartOfDay();
            long questions = conversationRepository.findByUserIdAndTimestampBetween(user.getId(), start, end).size();

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date.toString());
            item.put("label", date.getMonthValue() + "/" + date.getDayOfMonth());
            item.put("questions", questions);
            trends.add(item);
        }
        return trends;
    }

    private List<Map<String, Object>> buildTopKnowledgeBases(List<KnowledgeBase> knowledgeBases, List<FileUpload> completedFiles) {
        Map<String, Long> docCountByKbId = completedFiles.stream()
                .filter(file -> file.getKbId() != null && !file.getKbId().isEmpty())
                .collect(Collectors.groupingBy(FileUpload::getKbId, Collectors.counting()));

        return knowledgeBases.stream()
                .map(kb -> {
                    long count = docCountByKbId.getOrDefault(kb.getKbId(), 0L);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("kbId", kb.getKbId());
                    item.put("name", kb.getName());
                    item.put("count", count);
                    return item;
                })
                .sorted(Comparator
                        .comparing((Function<Map<String, Object>, Long>) item -> ((Number) item.get("count")).longValue())
                        .reversed()
                        .thenComparing(item -> String.valueOf(item.get("name"))))
                .limit(5)
                .toList();
    }

    private List<Map<String, Object>> buildFeatureUsage(long conversations, long documents, long knowledgeBases) {
        long total = conversations + documents + knowledgeBases;
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(buildFeatureUsageItem("智能对话", conversations, total, "#2563EB"));
        items.add(buildFeatureUsageItem("文档上传", documents, total, "#16A34A"));
        items.add(buildFeatureUsageItem("知识库管理", knowledgeBases, total, "#F59E0B"));
        return items;
    }

    private Map<String, Object> buildFeatureUsageItem(String label, long count, long total, String color) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("label", label);
        item.put("count", count);
        item.put("value", total > 0 ? Math.round(count * 1000.0 / total) / 10.0 : 0);
        item.put("color", color);
        return item;
    }

    // ==================== 登录记录相关接口 ====================

    /**
     * 获取当前用户的登录记录（分页）
     */
    @GetMapping("/login-records")
    public ResponseEntity<?> getLoginRecords(
            @RequestHeader("Authorization") String token,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("GET_LOGIN_RECORDS");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            if (username == null || username.isEmpty()) {
                monitor.end("获取登录记录失败：无效token");
                throw new CustomException("Invalid token", HttpStatus.UNAUTHORIZED);
            }

            Map<String, Object> records = userService.getLoginRecords(username, page, size);
            LogUtils.logUserOperation(username, "GET_LOGIN_RECORDS", "query", "SUCCESS");
            monitor.end("获取登录记录成功");

            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "Get login records successful",
                "data", records
            ));
        } catch (CustomException e) {
            LogUtils.logBusinessError("GET_LOGIN_RECORDS", username, "获取登录记录失败: %s", e, e.getMessage());
            monitor.end("获取登录记录失败: " + e.getMessage());
            return ResponseEntity.status(e.getStatus())
                    .body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_LOGIN_RECORDS", username, "获取登录记录异常: %s", e, e.getMessage());
            monitor.end("获取登录记录异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    /**
     * 获取当前用户的登录统计 + 最近记录
     */
    @GetMapping("/login-stats")
    public ResponseEntity<?> getLoginStatistics(@RequestHeader("Authorization") String token) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("GET_LOGIN_STATS");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            if (username == null || username.isEmpty()) {
                monitor.end("获取登录统计失败：无效token");
                throw new CustomException("Invalid token", HttpStatus.UNAUTHORIZED);
            }

            Map<String, Object> stats = userService.getLoginStatistics(username);
            LogUtils.logUserOperation(username, "GET_LOGIN_STATS", "query", "SUCCESS");
            monitor.end("获取登录统计成功");

            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "Get login statistics successful",
                "data", stats
            ));
        } catch (CustomException e) {
            LogUtils.logBusinessError("GET_LOGIN_STATS", username, "获取登录统计失败: %s", e, e.getMessage());
            monitor.end("获取登录统计失败: " + e.getMessage());
            return ResponseEntity.status(e.getStatus())
                    .body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_LOGIN_STATS", username, "获取登录统计异常: %s", e, e.getMessage());
            monitor.end("获取登录统计异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "Internal server error"));
        }
    }
}
