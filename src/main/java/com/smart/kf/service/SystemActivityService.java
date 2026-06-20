package com.smart.kf.service;

import com.smart.kf.model.FileUpload;
import com.smart.kf.model.KnowledgeBase;
import com.smart.kf.model.User;
import com.smart.kf.repository.FileUploadRepository;
import com.smart.kf.repository.KnowledgeBaseRepository;
import com.smart.kf.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SystemActivityService {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final FileUploadRepository fileUploadRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final UserRepository userRepository;

    public SystemActivityService(
        FileUploadRepository fileUploadRepository,
        KnowledgeBaseRepository knowledgeBaseRepository,
        UserRepository userRepository
    ) {
        this.fileUploadRepository = fileUploadRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.userRepository = userRepository;
    }

    public Map<String, Object> getRecentActivities() {
        List<Map<String, Object>> activities = new ArrayList<>();

        List<KnowledgeBase> knowledgeBases = knowledgeBaseRepository.findAll(PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "updatedAt"))).getContent();
        for (KnowledgeBase kb : knowledgeBases) {
            addActivity(activities, buildKnowledgeCreateActivity(kb));
            if (isDifferentMoment(kb.getCreatedAt(), kb.getUpdatedAt())) {
                addActivity(activities, buildKnowledgeUpdateActivity(kb));
            }
        }

        List<FileUpload> files = fileUploadRepository.findAll(PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "mergedAt"))).getContent();
        for (FileUpload file : files) {
            addActivity(activities, buildDocumentUploadActivity(file));
            if (file.getMergedAt() != null && isDifferentMoment(file.getCreatedAt(), file.getMergedAt())) {
                addActivity(activities, buildDocumentUpdateActivity(file));
            }
        }

        List<User> users = userRepository.findAll(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
        for (User user : users) {
            addActivity(activities, buildUserJoinActivity(user));
        }

        activities.sort(Comparator.<Map<String, Object>>comparingLong(item -> toLong(item.get("timestamp"))).reversed());
        List<Map<String, Object>> visibleActivities = activities.stream().limit(40).toList();

        Map<String, Object> stats = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekAgo = now.minusDays(7);
        stats.put("todayActivities", visibleActivities.stream().filter(item -> isSameDay(item.get("occurredAt"), now)).count());
        stats.put("weekActivities", visibleActivities.stream().filter(item -> !parseTime(item.get("occurredAt")).isBefore(weekAgo)).count());
        stats.put("knowledgeUpdates", visibleActivities.stream().filter(item -> "knowledge".equals(item.get("type")) && "更新知识库".equals(item.get("title"))).count());
        stats.put("documentUpdates", visibleActivities.stream().filter(item -> "document".equals(item.get("type")) && "更新文档".equals(item.get("title"))).count());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activities", visibleActivities);
        result.put("stats", stats);
        return result;
    }

    private void addActivity(List<Map<String, Object>> activities, Map<String, Object> activity) {
        if (activity == null || toLong(activity.get("timestamp")) <= 0) {
            return;
        }
        activities.add(activity);
    }

    private Map<String, Object> buildKnowledgeCreateActivity(KnowledgeBase kb) {
        return buildActivity(
            "kb-create-" + kb.getKbId(),
            "knowledge",
            "data-base",
            "创建知识库",
            "知识库「" + safeText(kb.getName(), "未命名") + "」已创建",
            kb.getCreatedAt(),
            "green"
        );
    }

    private Map<String, Object> buildKnowledgeUpdateActivity(KnowledgeBase kb) {
        return buildActivity(
            "kb-update-" + kb.getKbId(),
            "knowledge",
            "settings-adjust",
            "更新知识库",
            "知识库「" + safeText(kb.getName(), "未命名") + "」信息已更新",
            kb.getUpdatedAt(),
            "cyan"
        );
    }

    private Map<String, Object> buildDocumentUploadActivity(FileUpload file) {
        String targetName = safeText(file.getKbId(), safeText(file.getOrgTag(), "未归类"));
        return buildActivity(
            "doc-create-" + safeText(file.getFileMd5(), safeText(file.getFileName(), "unknown")),
            "document",
            "document-add",
            "上传文档",
            "文件「" + safeText(file.getFileName(), "未知") + "」上传到「" + targetName + "」",
            file.getCreatedAt(),
            "blue"
        );
    }

    private Map<String, Object> buildDocumentUpdateActivity(FileUpload file) {
        return buildActivity(
            "doc-update-" + safeText(file.getFileMd5(), safeText(file.getFileName(), "unknown")),
            "document",
            "document-tasks",
            "更新文档",
            "文件「" + safeText(file.getFileName(), "未知") + "」已完成处理并更新索引",
            file.getMergedAt(),
            "purple"
        );
    }

    private Map<String, Object> buildUserJoinActivity(User user) {
        return buildActivity(
            "user-" + (user.getId() != null ? user.getId() : safeText(user.getUsername(), "unknown")),
            "user",
            "user-follow",
            "用户加入",
            "用户「" + safeText(user.getUsername(), "未知") + "」已加入系统",
            user.getCreatedAt(),
            "orange"
        );
    }

    private Map<String, Object> buildActivity(
        String id,
        String type,
        String icon,
        String title,
        String description,
        LocalDateTime occurredAt,
        String color
    ) {
        if (occurredAt == null) {
            return null;
        }
        Map<String, Object> activity = new LinkedHashMap<>();
        activity.put("id", id);
        activity.put("type", type);
        activity.put("icon", icon);
        activity.put("title", title);
        activity.put("description", description);
        activity.put("occurredAt", occurredAt.format(ISO_FORMATTER));
        activity.put("timestamp", occurredAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        activity.put("color", color);
        return activity;
    }

    private boolean isDifferentMoment(LocalDateTime left, LocalDateTime right) {
        if (left == null || right == null) {
            return false;
        }
        return Math.abs(java.time.Duration.between(left, right).toMillis()) >= 60_000;
    }

    private boolean isSameDay(Object occurredAt, LocalDateTime now) {
        LocalDateTime parsed = parseTime(occurredAt);
        return parsed.toLocalDate().equals(now.toLocalDate());
    }

    private LocalDateTime parseTime(Object occurredAt) {
        if (occurredAt instanceof String text && !text.isBlank()) {
            return LocalDateTime.parse(text, ISO_FORMATTER);
        }
        return LocalDateTime.MIN;
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
