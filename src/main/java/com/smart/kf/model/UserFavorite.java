package com.smart.kf.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user_favorites", indexes = {
        @Index(name = "idx_user_favorite_user", columnList = "user_id"),
        @Index(name = "idx_user_favorite_type", columnList = "type")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_favorite_target", columnNames = {"user_id", "type", "target_id"})
})
public class UserFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(name = "target_id", nullable = false, length = 128)
    private String targetId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 128)
    private String meta;

    @Column(nullable = false)
    private boolean starred = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
