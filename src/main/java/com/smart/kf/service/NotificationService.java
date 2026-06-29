package com.smart.kf.service;

import com.smart.kf.model.UserNotification;
import com.smart.kf.repository.UserNotificationRepository;
import com.smart.kf.repository.UserRepository;
import com.smart.kf.utils.LogUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户通知服务
 * 负责在管理员操作用户资源时生成并发送通知
 */
@Service
public class NotificationService {

    @Autowired
    private UserNotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * 发送通知：admin 操作他人知识库时调用
     *
     * @param recipientUsername 接收通知的用户名（资源拥有者）
     * @param operatorUsername  操作者（admin）用户名
     * @param actionType        操作类型（UPDATE_KB / DELETE_KB / UPDATE_DOCUMENT / DELETE_DOCUMENT）
     * @param resourceId        资源标识（kbId 或 fileMd5）
     * @param resourceName      资源名称（知识库名 或 文件名）
     */
    public void sendNotification(String recipientUsername, String operatorUsername,
                                  String actionType, String resourceId, String resourceName) {
        // 不给自己发通知
        if (recipientUsername == null || recipientUsername.equals(operatorUsername)) {
            return;
        }

        UserNotification notification = new UserNotification();
        notification.setRecipientUsername(recipientUsername);
        notification.setOperatorUsername(operatorUsername);
        // 同时写入 FK 字段，已有 username 列保留用于历史审计
        userRepository.findByUsername(recipientUsername).ifPresent(u -> notification.setRecipientId(u.getId()));
        userRepository.findByUsername(operatorUsername).ifPresent(u -> notification.setOperatorId(u.getId()));
        notification.setActionType(actionType);
        notification.setResourceId(resourceId);
        notification.setResourceName(resourceName);
        notification.setMessage(buildMessage(actionType, operatorUsername, resourceName));
        notification.setRead(false);

        notificationRepository.save(notification);
        LogUtils.logBusiness("NOTIFICATION", operatorUsername,
            "已向用户 %s 发送通知: actionType=%s, resourceName=%s", recipientUsername, actionType, resourceName);
    }

    /**
     * 查询指定用户的所有通知（倒序）
     */
    public List<UserNotification> getUserNotifications(String username) {
        return notificationRepository.findByRecipientUsernameOrderByCreatedAtDesc(username);
    }

    /**
     * 查询指定用户的未读通知
     */
    public List<UserNotification> getUnreadNotifications(String username) {
        return notificationRepository.findByRecipientUsernameAndIsReadFalseOrderByCreatedAtDesc(username);
    }

    /**
     * 查询指定用户的未读通知数量
     */
    public long getUnreadCount(String username) {
        return notificationRepository.countByRecipientUsernameAndIsReadFalse(username);
    }

    /**
     * 将单条通知标记为已读
     */
    @Transactional
    public boolean markAsRead(Long notificationId, String username) {
        return notificationRepository.findById(notificationId).map(n -> {
            if (!n.getRecipientUsername().equals(username)) {
                return false;
            }
            n.setRead(true);
            notificationRepository.save(n);
            return true;
        }).orElse(false);
    }

    /**
     * 将当前用户所有通知标记为已读
     */
    @Transactional
    public int markAllAsRead(String username) {
        return notificationRepository.markAllAsRead(username);
    }

    // ----------------------------- private helpers -----------------------------

    private String buildMessage(String actionType, String operatorUsername, String resourceName) {
        return switch (actionType) {
            case "UPDATE_KB"       -> String.format("管理员 [%s] 修改了您的知识库「%s」", operatorUsername, resourceName);
            case "DELETE_KB"       -> String.format("管理员 [%s] 删除了您的知识库「%s」", operatorUsername, resourceName);
            case "UPDATE_DOCUMENT" -> String.format("管理员 [%s] 修改了您的文档「%s」", operatorUsername, resourceName);
            case "DELETE_DOCUMENT" -> String.format("管理员 [%s] 删除了您的文档「%s」", operatorUsername, resourceName);
            default                -> String.format("管理员 [%s] 对您的资源「%s」执行了操作：%s", operatorUsername, resourceName, actionType);
        };
    }
}
