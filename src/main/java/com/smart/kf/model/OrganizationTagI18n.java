package com.smart.kf.model;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Per-language overrides for organization tag name and description.
 * Row format: (tagId, lang) → (name?, description?)
 * zh-CN content lives on the OrganizationTag entity itself; only non-Chinese translations go here.
 */
@Data
@Entity
@Table(
    name = "organization_tag_i18n",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tag_id", "lang"})
)
public class OrganizationTagI18n {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tag_id", nullable = false, length = 100)
    private String tagId;

    @Column(name = "lang", nullable = false, length = 10)
    private String lang;

    @Column(name = "name", length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}
