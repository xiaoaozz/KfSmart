package com.smart.kf.controller.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.kf.exception.CustomException;
import com.smart.kf.model.FileUpload;
import com.smart.kf.model.User;
import com.smart.kf.repository.ConversationRepository;
import com.smart.kf.repository.FileUploadRepository;
import com.smart.kf.repository.OrganizationTagRepository;
import com.smart.kf.repository.UserRepository;
import com.smart.kf.service.AdminAuthHelper;
import com.smart.kf.service.SystemActivityService;
import com.smart.kf.utils.JwtUtils;
import com.smart.kf.utils.LogUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 管理员统计分析相关接口：系统统计、最近活动、用户活动日志、对话历史查询、操作日志查询。
 * 从原 AdminController 拆分而来，逻辑保持一致。
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAuthority('system:admin')")
@RequiredArgsConstructor
public class AdminAnalyticsController {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final FileUploadRepository fileUploadRepository;
    private final OrganizationTagRepository organizationTagRepository;
    private final SystemActivityService systemActivityService;
    private final JwtUtils jwtUtils;
    private final AdminAuthHelper adminAuthHelper;

    /**
     * 获取系统统计数据（从数据库实时查询）
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getSystemStats(@RequestHeader("Authorization") String token) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("ADMIN_GET_SYSTEM_STATS");
        String adminUsername = null;
        try {
            adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            adminAuthHelper.validateAdmin(adminUsername);

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
            adminAuthHelper.validateAdmin(adminUsername);

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
            adminAuthHelper.validateAdmin(adminUsername);

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
            adminAuthHelper.validateAdmin(adminUsername);

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

    /**
     * 获取操作日志（分页）
     */
    @GetMapping("/activity-logs")
    public ResponseEntity<?> getActivityLogs(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        String adminUsername;
        try {
            adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            adminAuthHelper.validateAdmin(adminUsername);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", 401, "message", "Unauthorized"));
        }

        try {
            int page = current != null ? current : 1;
            int pageSize = size != null ? size : 20;
            int offset = (page - 1) * pageSize;

            // 从 SystemActivityService 获取日志
            Map<String, Object> activityData = systemActivityService.getRecentActivities();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> allLogs = (List<Map<String, Object>>) activityData.getOrDefault("activities", List.of());
            if (allLogs == null) allLogs = List.of();

            // 过滤
            if (keyword != null && !keyword.isBlank()) {
                String kw = keyword.toLowerCase();
                allLogs = allLogs.stream()
                    .filter(log -> {
                        String description = String.valueOf(log.getOrDefault("description", ""));
                        String title = String.valueOf(log.getOrDefault("title", ""));
                        return description.toLowerCase().contains(kw) || title.toLowerCase().contains(kw);
                    })
                    .toList();
            }
            if (action != null && !action.isBlank()) {
                allLogs = allLogs.stream()
                    .filter(log -> action.equals(log.get("title")))
                    .toList();
            }
            if (status != null && !status.isBlank()) {
                allLogs = allLogs.stream()
                    .filter(log -> status.equals(log.get("status")))
                    .toList();
            }
            if (startTime != null && !startTime.isBlank()) {
                LocalDateTime start = startTime.contains("T")
                    ? LocalDateTime.parse(startTime)
                    : LocalDate.parse(startTime).atStartOfDay();
                allLogs = allLogs.stream()
                    .filter(log -> {
                        String ts = String.valueOf(log.getOrDefault("occurredAt", ""));
                        try { return LocalDateTime.parse(ts).isAfter(start); } catch (Exception e) { return true; }
                    })
                    .toList();
            }
            if (endTime != null && !endTime.isBlank()) {
                LocalDateTime end = endTime.contains("T")
                    ? LocalDateTime.parse(endTime)
                    : LocalDate.parse(endTime).atTime(23, 59, 59);
                allLogs = allLogs.stream()
                    .filter(log -> {
                        String ts = String.valueOf(log.getOrDefault("occurredAt", ""));
                        try { return LocalDateTime.parse(ts).isBefore(end); } catch (Exception e) { return true; }
                    })
                    .toList();
            }

            int total = allLogs.size();
            int end = Math.min(offset + pageSize, total);
            List<Map<String, Object>> pageData = offset < total ? allLogs.subList(offset, end) : List.of();

            return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", Map.of(
                    "records", pageData,
                    "total", total,
                    "current", page,
                    "size", pageSize
                )
            ));
        } catch (Exception e) {
            LogUtils.logBusinessError("ACTIVITY_LOGS", adminUsername, "获取操作日志失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取操作日志失败: " + e.getMessage()));
        }
    }

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
}
