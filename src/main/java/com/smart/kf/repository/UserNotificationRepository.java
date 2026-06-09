package com.smart.kf.repository;

import com.smart.kf.model.UserNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

    /**
     * 查询指定用户的所有通知（按创建时间倒序）
     */
    List<UserNotification> findByRecipientUsernameOrderByCreatedAtDesc(String recipientUsername);

    /**
     * 查询指定用户的未读通知
     */
    List<UserNotification> findByRecipientUsernameAndIsReadFalseOrderByCreatedAtDesc(String recipientUsername);

    /**
     * 查询指定用户的未读通知数量
     */
    long countByRecipientUsernameAndIsReadFalse(String recipientUsername);

    /**
     * 将指定用户的所有通知标记为已读
     */
    @Modifying
    @Query("UPDATE UserNotification n SET n.isRead = true WHERE n.recipientUsername = :username")
    int markAllAsRead(@Param("username") String username);
}
