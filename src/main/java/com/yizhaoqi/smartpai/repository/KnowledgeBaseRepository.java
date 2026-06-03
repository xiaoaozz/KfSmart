package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {
    
    Optional<KnowledgeBase> findByKbId(String kbId);
    
    boolean existsByName(String name);
}