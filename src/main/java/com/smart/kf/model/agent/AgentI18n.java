package com.smart.kf.model.agent;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Per-language overrides for agent name and description.
 * Row format: (agentId, lang) → (name?, description?)
 * zh-CN content lives on the Agent entity itself; only non-Chinese translations go here.
 */
@Data
@Entity
@Table(
    name = "agent_i18n",
    uniqueConstraints = @UniqueConstraint(columnNames = {"agent_id", "lang"})
)
public class AgentI18n {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "lang", nullable = false, length = 10)
    private String lang;

    @Column(name = "name")
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}
