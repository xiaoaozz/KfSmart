package com.smart.kf.repository.agent;

import com.smart.kf.model.agent.SkillVersionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillVersionHistoryRepository extends JpaRepository<SkillVersionHistory, Long> {
    List<SkillVersionHistory> findBySkillIdOrderBySnapshotAtDesc(String skillId);
}
