package com.smart.kf.repository;

import com.smart.kf.model.ConversationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationSessionRepository extends JpaRepository<ConversationSession, Long> {

    Optional<ConversationSession> findBySessionId(String sessionId);

    List<ConversationSession> findByUser_IdOrderByUpdatedAtDesc(Long userId);

    List<ConversationSession> findByUser_IdAndSessionTypeOrderByUpdatedAtDesc(Long userId, String sessionType);

    void deleteBySessionId(String sessionId);
}
