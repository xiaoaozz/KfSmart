package com.smart.kf.repository;

import com.smart.kf.model.User;
import com.smart.kf.model.UserFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserFavoriteRepository extends JpaRepository<UserFavorite, Long> {

    List<UserFavorite> findByUserOrderByUpdatedAtDesc(User user);

    Optional<UserFavorite> findByIdAndUser(Long id, User user);

    Optional<UserFavorite> findByUserAndTypeAndTargetId(User user, String type, String targetId);

    long countByUser(User user);
}
