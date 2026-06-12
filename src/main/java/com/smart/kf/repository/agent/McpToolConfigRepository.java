package com.smart.kf.repository.agent;

import com.smart.kf.model.agent.McpToolConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface McpToolConfigRepository extends JpaRepository<McpToolConfig, Long> {
    Optional<McpToolConfig> findByToolId(String toolId);

    List<McpToolConfig> findByNameContainingIgnoreCaseOrTypeContainingIgnoreCase(String name, String type);
}
