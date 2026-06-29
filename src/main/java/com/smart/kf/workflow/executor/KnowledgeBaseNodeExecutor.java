package com.smart.kf.workflow.executor;

import com.smart.kf.entity.SearchResult;
import com.smart.kf.service.HybridSearchService;
import com.smart.kf.workflow.engine.ExecutionContext;
import com.smart.kf.workflow.engine.NodeExecutionResult;
import com.smart.kf.workflow.engine.NodeExecutor;
import com.smart.kf.workflow.model.WorkflowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class KnowledgeBaseNodeExecutor implements NodeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseNodeExecutor.class);

    private final HybridSearchService hybridSearchService;

    public KnowledgeBaseNodeExecutor(HybridSearchService hybridSearchService) {
        this.hybridSearchService = hybridSearchService;
    }

    @Override
    public String getNodeType() {
        return "知识库检索";
    }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, ExecutionContext ctx) {
        String knowledgeBase = node.configString("knowledgeBase");
        int topK = toInt(node.configObject("topK"));

        // 即时调试覆盖
        String debugKb = (String) ctx.getVariable("debug_knowledgeBases");
        if (debugKb != null && !debugKb.isBlank()) {
            knowledgeBase = debugKb;
        }

        if (knowledgeBase == null || knowledgeBase.isBlank()) {
            logger.info("知识库检索节点未配置知识库，跳过");
            ctx.setVariableIfAbsent("documents", List.of());
            ctx.setVariableIfAbsent("context", "");
            Map<String, Object> outputs = new HashMap<>();
            outputs.put("documents", List.of());
            outputs.put("context", "");
            return NodeExecutionResult.of(outputs, "未配置知识库，跳过检索");
        }

        String queryTemplate = node.configString("query");
        String query;
        if (queryTemplate != null && !queryTemplate.isBlank()) {
            query = ctx.resolveTemplate(queryTemplate);
        } else {
            query = String.valueOf(ctx.getOrDefault("query", ""));
        }
        List<SearchResult> documents = hybridSearchService.searchWithPermission(query, ctx.getUsername(), topK);

        String context = documents.stream()
            .map(doc -> "来源：" + (doc.getFileName() == null ? doc.getFileMd5() : doc.getFileName()) + "\n" + doc.getTextContent())
            .collect(Collectors.joining("\n\n"));

        ctx.setVariable("documents", documents);
        ctx.setVariable("context", context);

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("documents", documents);
        outputs.put("context", context);
        return NodeExecutionResult.of(outputs, "检索知识库[" + knowledgeBase + "]，返回" + documents.size() + "条相关文档，topK=" + topK);
    }

    private int toInt(Object val) {
        return toInt(val, 5);
    }

    @SuppressWarnings("sameParameterValue")
    private int toInt(Object val, int defaultValue) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (Exception ignored) {}
        }
        return defaultValue;
    }
}
