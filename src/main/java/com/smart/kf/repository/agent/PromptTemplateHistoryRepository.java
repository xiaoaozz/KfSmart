package com.smart.kf.repository.agent;

import com.smart.kf.model.agent.PromptTemplateHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromptTemplateHistoryRepository extends JpaRepository<PromptTemplateHistory, Long> {
    List<PromptTemplateHistory> findByTemplateIdOrderBySnapshotAtDesc(String templateId);
}
