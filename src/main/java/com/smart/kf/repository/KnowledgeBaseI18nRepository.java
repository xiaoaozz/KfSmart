package com.smart.kf.repository;

import com.smart.kf.model.KnowledgeBaseI18n;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseI18nRepository extends JpaRepository<KnowledgeBaseI18n, Long> {

    Optional<KnowledgeBaseI18n> findByKbIdAndLang(String kbId, String lang);

    List<KnowledgeBaseI18n> findByKbId(String kbId);
}
