package com.smart.kf.repository;

import com.smart.kf.model.KnowledgeBase;
import com.smart.kf.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {
    
    Optional<KnowledgeBase> findByKbId(String kbId);

    List<KnowledgeBase> findByCreatedBy(User createdBy);
    
    boolean existsByName(String name);
}
