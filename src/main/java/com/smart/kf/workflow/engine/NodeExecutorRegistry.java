package com.smart.kf.workflow.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 节点执行器注册中心。
 * <p>
 * Spring 启动时自动收集所有 {@link NodeExecutor} Bean，按类型建立索引。
 * 引擎通过此注册中心查找节点对应的执行器。
 */
@Component
public class NodeExecutorRegistry {

    private static final Logger logger = LoggerFactory.getLogger(NodeExecutorRegistry.class);

    private static final Map<String, String> ALIASES = Map.ofEntries(
        Map.entry("start", "开始"),
        Map.entry("end", "结束"),
        Map.entry("llm", "LLM"),
        Map.entry("kb", "知识库检索"),
        Map.entry("code", "代码执行"),
        Map.entry("condition", "条件判断"),
        Map.entry("http", "HTTP请求"),
        Map.entry("loop", "循环"),
        Map.entry("variable", "变量"),
        Map.entry("agent_call", "Agent调用"),
        Map.entry("delay", "延迟")
    );

    private final Map<String, NodeExecutor> executors = new HashMap<>();

    public NodeExecutorRegistry(List<NodeExecutor> executorList) {
        for (NodeExecutor executor : executorList) {
            String type = executor.getNodeType();
            if (type != null && !type.isBlank()) {
                executors.put(type, executor);
                logger.info("注册节点执行器: type={} → {}", type, executor.getClass().getSimpleName());
            }
        }
        logger.info("共注册 {} 个节点执行器", executors.size());
    }

    /**
     * 获取节点执行器，支持模糊匹配（节点类型包含关键词）。
     * 例如节点类型为 "LLM生成" 或 "LLM" 均可匹配注册为 "LLM" 的执行器。
     */
    public NodeExecutor getExecutor(String nodeType) {
        if (nodeType == null) {
            return null;
        }
        NodeExecutor exact = executors.get(nodeType);
        if (exact != null) {
            return exact;
        }
        String alias = ALIASES.get(nodeType);
        if (alias != null) {
            NodeExecutor mapped = executors.get(alias);
            if (mapped != null) {
                return mapped;
            }
        }
        for (Map.Entry<String, NodeExecutor> entry : executors.entrySet()) {
            if (nodeType.contains(entry.getKey()) || entry.getKey().contains(nodeType)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public int size() {
        return executors.size();
    }
}
