package com.smart.kf.workflow.engine.dag;

import com.smart.kf.workflow.model.WorkflowEdge;
import com.smart.kf.workflow.model.WorkflowNode;

import java.util.*;

/**
 * Kahn 拓扑排序算法 + 环检测。
 * 用于验证工作流 DAG 合法性并计算执行顺序。
 */
public class TopologicalSorter {

    /**
     * 对图做拓扑排序，返回按依赖顺序排列的节点列表。
     *
     * @throws IllegalArgumentException 如果检测到环
     */
    public List<String> sort(WorkflowGraph graph) {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adjacency = new HashMap<>();

        for (WorkflowNode node : graph.getAllNodes()) {
            inDegree.putIfAbsent(node.id(), 0);
            adjacency.putIfAbsent(node.id(), new ArrayList<>());
        }

        for (WorkflowEdge edge : graph.getAllEdges()) {
            if (!adjacency.containsKey(edge.source())) {
                adjacency.put(edge.source(), new ArrayList<>());
                inDegree.put(edge.source(), 0);
            }
            if (!inDegree.containsKey(edge.target())) {
                inDegree.put(edge.target(), 0);
                adjacency.put(edge.target(), new ArrayList<>());
            }
            adjacency.get(edge.source()).add(edge.target());
            inDegree.merge(edge.target(), 1, Integer::sum);
        }

        Queue<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!sorted.contains(current)) {
                sorted.add(current);
            }
            for (String neighbor : adjacency.getOrDefault(current, List.of())) {
                int newDegree = inDegree.merge(neighbor, -1, Integer::sum);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (sorted.size() != inDegree.size()) {
            throw new IllegalArgumentException("工作流存在循环依赖（DAG 校验失败），请检查节点连线");
        }

        return sorted;
    }
}
