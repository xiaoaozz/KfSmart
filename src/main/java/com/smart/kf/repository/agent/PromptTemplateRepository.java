package com.smart.kf.repository.agent;

import com.smart.kf.model.agent.PromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Long> {
    Optional<PromptTemplate> findByTemplateId(String templateId);

    List<PromptTemplate> findByNameContainingIgnoreCaseOrCategoryContainingIgnoreCase(String name, String category);

    List<PromptTemplate> findByCategory(String category);

    List<PromptTemplate> findByCategoryAndNameContainingIgnoreCase(String category, String name);
}
