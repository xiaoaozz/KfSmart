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
