package com.smart.kf.agent.engine.tools;

import com.smart.kf.agent.engine.ToolResult;
import com.smart.kf.service.HybridSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class KnowledgeBaseTool {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseTool.class);

    private final HybridSearchService hybridSearchService;

    public KnowledgeBaseTool(HybridSearchService hybridSearchService) {
        this.hybridSearchService = hybridSearchService;
    }

    public ToolResult search(String query, String knowledgeBases) {
        if (knowledgeBases != null && !knowledgeBases.isBlank()) {
            logger.debug("知识库检索范围: {}", knowledgeBases);
        }
        try {
            var results = hybridSearchService.search(query, 5);
            if (results == null || results.isEmpty()) {
                return ToolResult.success("未找到相关文档。");
            }
            String context = results.stream()
                .map(r -> r.getTextContent() != null ? r.getTextContent() : r.toString())
                .collect(Collectors.joining("\n---\n"));
            logger.info("知识库检索完成: query={}, results={}", query, results.size());
            return ToolResult.success(context);
        } catch (Exception e) {
            logger.error("知识库检索失败: {}", e.getMessage(), e);
            return ToolResult.failure("知识库检索失败: " + e.getMessage());
        }
    }
}
