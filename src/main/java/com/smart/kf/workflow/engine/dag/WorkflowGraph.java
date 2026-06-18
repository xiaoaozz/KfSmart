package com.smart.kf.workflow.engine.dag;

import com.smart.kf.workflow.model.WorkflowEdge;
import com.smart.kf.workflow.model.WorkflowNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流图结构。
 * 持有节点和边的索引，提供快速查询能力。
 */
public class WorkflowGraph {

    private final Map<String, WorkflowNode> nodes;
    private final List<WorkflowEdge> edges;
    private final Map<String, List<WorkflowEdge>> outgoingEdges;
    private final Map<String, List<WorkflowEdge>> incomingEdges;

    public WorkflowGraph(List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        this.nodes = new LinkedHashMap<>();
        this.edges = new ArrayList<>(edges);
        this.outgoingEdges = new LinkedHashMap<>();
        this.incomingEdges = new LinkedHashMap<>();

        for (WorkflowNode node : nodes) {
            this.nodes.put(node.id(), node);
            this.outgoingEdges.put(node.id(), new ArrayList<>());
            this.incomingEdges.put(node.id(), new ArrayList<>());
        }
        for (WorkflowEdge edge : edges) {
            this.outgoingEdges.computeIfAbsent(edge.source(), k -> new ArrayList<>()).add(edge);
            this.incomingEdges.computeIfAbsent(edge.target(), k -> new ArrayList<>()).add(edge);
        }
    }

    public WorkflowNode getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    public List<WorkflowNode> getAllNodes() {
        return new ArrayList<>(nodes.values());
    }

    public List<WorkflowEdge> getAllEdges() {
        return new ArrayList<>(edges);
    }

    public List<WorkflowEdge> getOutgoingEdges(String nodeId) {
        return outgoingEdges.getOrDefault(nodeId, List.of());
    }

    public List<WorkflowEdge> getIncomingEdges(String nodeId) {
        return incomingEdges.getOrDefault(nodeId, List.of());
    }

    /**
     * 找到开始节点（类型包含"开始"的节点，否则返回第一个节点）。
     */
    public WorkflowNode getStartNode() {
        return nodes.values().stream()
            .filter(node -> node.type() != null && node.type().contains("开始"))
            .findFirst()
            .orElse(nodes.isEmpty() ? null : nodes.values().iterator().next());
    }

    /**
     * 找到结束节点（类型包含"结束"或"输出"的节点）。
     */
    public WorkflowNode getEndNode() {
        return nodes.values().stream()
            .filter(node -> node.type() != null && (node.type().contains("结束") || node.type().contains("输出")))
            .findFirst()
            .orElse(null);
    }

    public int nodeCount() {
        return nodes.size();
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }
}
