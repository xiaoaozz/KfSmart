package com.smart.kf.controller;

import com.smart.kf.model.UserNotification;
import com.smart.kf.service.NotificationService;
import com.smart.kf.utils.JwtUtils;
import com.smart.kf.utils.LogUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 通知控制器
 * 用户可查询自己的通知、查看未读数量、标记通知为已读
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private JwtUtils jwtUtils;

    /**
     * 获取当前用户的所有通知（按时间倒序）
     */
    @GetMapping
    public ResponseEntity<?> getNotifications(
            @RequestHeader("Authorization") String token) {

        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            List<UserNotification> notifications = notificationService.getUserNotifications(username);
            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "获取通知列表成功",
                "data", notifications
            ));
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_NOTIFICATIONS", username, "获取通知列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", 500, "message", "获取通知列表失败: " + e.getMessage()));
        }
    }

    /**
     * 获取当前用户的未读通知数量
     */
    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(
            @RequestHeader("Authorization") String token) {

        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            long count = notificationService.getUnreadCount(username);
            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "获取未读通知数量成功",
                "data", Map.of("unreadCount", count)
            ));
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_UNREAD_COUNT", username, "获取未读通知数量失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", 500, "message", "获取未读通知数量失败: " + e.getMessage()));
        }
    }

    /**
     * 将指定通知标记为已读
     */
    @PutMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {

        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            boolean success = notificationService.markAsRead(id, username);
            if (!success) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("code", 404, "message", "通知不存在或无权操作"));
            }
            return ResponseEntity.ok(Map.of("code", 200, "message", "已标记为已读"));
        } catch (Exception e) {
            LogUtils.logBusinessError("MARK_NOTIFICATION_READ", username, "标记通知已读失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", 500, "message", "标记通知已读失败: " + e.getMessage()));
        }
    }

    /**
     * 将当前用户所有通知标记为已读
     */
    @PutMapping("/read-all")
    public ResponseEntity<?> markAllAsRead(
            @RequestHeader("Authorization") String token) {

        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            int count = notificationService.markAllAsRead(username);
            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "已全部标记为已读",
                "data", Map.of("updatedCount", count)
            ));
        } catch (Exception e) {
            LogUtils.logBusinessError("MARK_ALL_NOTIFICATIONS_READ", username, "全部标记已读失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", 500, "message", "全部标记已读失败: " + e.getMessage()));
        }
    }
}
