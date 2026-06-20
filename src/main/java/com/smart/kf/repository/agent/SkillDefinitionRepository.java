package com.smart.kf.repository.agent;

import com.smart.kf.model.agent.SkillDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SkillDefinitionRepository extends JpaRepository<SkillDefinition, Long> {
    Optional<SkillDefinition> findBySkillId(String skillId);
}
