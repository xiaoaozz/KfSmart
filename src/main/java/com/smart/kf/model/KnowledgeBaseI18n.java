package com.smart.kf.model;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Per-language overrides for knowledge base name and description.
 * Row format: (kbId, lang) → (name?, description?)
 * zh-CN content lives on the KnowledgeBase entity itself; only non-Chinese translations go here.
 */
@Data
@Entity
@Table(
    name = "knowledge_base_i18n",
    uniqueConstraints = @UniqueConstraint(columnNames = {"kb_id", "lang"})
)
public class KnowledgeBaseI18n {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kb_id", nullable = false)
    private String kbId;

    @Column(name = "lang", nullable = false, length = 10)
    private String lang;

    @Column(name = "name")
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}
