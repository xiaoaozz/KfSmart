package com.smart.kf.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.kf.model.agent.Agent;
import com.smart.kf.model.agent.AgentRunAnalysisSnapshot;
import com.smart.kf.repository.agent.AgentRepository;
import com.smart.kf.repository.agent.AgentRunAnalysisSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentRunAnalysisService {

    private final AgentRepository agentRepository;
    private final AgentRunAnalysisSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    public AgentRunAnalysisService(
        AgentRepository agentRepository,
        AgentRunAnalysisSnapshotRepository snapshotRepository,
        ObjectMapper objectMapper
    ) {
        this.agentRepository = agentRepository;
        this.snapshotRepository = snapshotRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void recordExecution(boolean success, long durationMs, int totalTokens, BigDecimal modelCost, BigDecimal toolCost) {
        if (snapshotRepository.count() == 0) {
            seedSnapshotFromCurrentAgents();
        }

        LocalDate snapshotDate = LocalDate.now();
        AgentRunAnalysisSnapshot snapshot = snapshotRepository.findBySnapshotDate(snapshotDate).orElseGet(() -> {
            AgentRunAnalysisSnapshot created = new AgentRunAnalysisSnapshot();
            created.setSnapshotDate(snapshotDate);
            return created;
        });

        snapshot.setRunCount(snapshot.getRunCount() + 1);
        if (success) {
            snapshot.setSuccessCount(snapshot.getSuccessCount() + 1);
        } else {
            snapshot.setFailureCount(snapshot.getFailureCount() + 1);
        }
        snapshot.setDurationTotalMs(snapshot.getDurationTotalMs() + Math.max(0L, durationMs));
        snapshot.setTokenUsage(snapshot.getTokenUsage() + Math.max(0, totalTokens));

        snapshot.setModelCost(snapshot.getModelCost().add(safeBigDecimal(modelCost).setScale(6, RoundingMode.HALF_UP)));
        snapshot.setToolCost(snapshot.getToolCost().add(safeBigDecimal(toolCost).setScale(6, RoundingMode.HALF_UP)));

        snapshot.setHotAgentsJson(serializeHotAgents());
        snapshotRepository.save(snapshot);
    }

    public Map<String, Object> buildRunAnalysis() {
        if (snapshotRepository.count() == 0) {
            seedSnapshotFromCurrentAgents();
        }

        List<Agent> agents = agentRepository.findAll();
        List<AgentRunAnalysisSnapshot> snapshots = snapshotRepository.findAll();
        SnapshotSummary summary = summarizeSnapshots(snapshots);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("agentCount", agents.size());
        result.put("runCount", summary.runCount);
        result.put("successCount", summary.successCount);
        result.put("failureCount", summary.failureCount);
        result.put("successRate", summary.runCount == 0 ? 100 : Math.round(summary.successCount * 100.0 / summary.runCount));
        result.put("failureRate", summary.runCount == 0 ? 0 : Math.round(summary.failureCount * 100.0 / summary.runCount));
        result.put("avgDurationMs", summary.runCount == 0 ? 0 : Math.round(summary.durationTotalMs * 1.0 / summary.runCount));
        result.put("hotAgents", getHotAgents(agents));
        result.put("cost", Map.of(
            "tokenUsage", summary.tokenUsage,
            "modelCost", summary.modelCost.doubleValue(),
            "toolCost", summary.toolCost.doubleValue()
        ));
        result.put("dailyTrends", buildDailyTrends(snapshots));
        result.put("latestSnapshotDate", summary.latestSnapshotDate != null ? summary.latestSnapshotDate.toString() : null);
        return result;
    }

    @Transactional
    protected void seedSnapshotFromCurrentAgents() {
        if (snapshotRepository.count() > 0) {
            return;
        }

        List<Agent> agents = agentRepository.findAll();
        long runCount = agents.stream().mapToLong(a -> a.getCallCount() != null ? a.getCallCount() : 0L).sum();
        long successCount = agents.stream().mapToLong(a -> a.getSuccessCount() != null ? a.getSuccessCount() : 0L).sum();
        long failureCount = agents.stream().mapToLong(a -> a.getFailureCount() != null ? a.getFailureCount() : 0L).sum();
        long durationTotalMs = agents.stream().mapToLong(item -> {
            long avg = item.getAvgDurationMs() != null ? item.getAvgDurationMs() : 0L;
            long cnt = item.getCallCount() != null ? item.getCallCount() : 0L;
            return avg * cnt;
        }).sum();

        AgentRunAnalysisSnapshot snapshot = new AgentRunAnalysisSnapshot();
        snapshot.setSnapshotDate(LocalDate.now());
        snapshot.setRunCount(runCount);
        snapshot.setSuccessCount(successCount);
        snapshot.setFailureCount(failureCount);
        snapshot.setDurationTotalMs(durationTotalMs);
        snapshot.setTokenUsage(runCount * 1280L);
        snapshot.setModelCost(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP));
        snapshot.setToolCost(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP));
        snapshot.setHotAgentsJson(serializeHotAgents());
        snapshotRepository.save(snapshot);
    }

    private SnapshotSummary summarizeSnapshots(List<AgentRunAnalysisSnapshot> snapshots) {
        SnapshotSummary summary = new SnapshotSummary();
        for (AgentRunAnalysisSnapshot snapshot : snapshots) {
            summary.runCount += safeLong(snapshot.getRunCount());
            summary.successCount += safeLong(snapshot.getSuccessCount());
            summary.failureCount += safeLong(snapshot.getFailureCount());
            summary.durationTotalMs += safeLong(snapshot.getDurationTotalMs());
            summary.tokenUsage += safeLong(snapshot.getTokenUsage());
            summary.modelCost = summary.modelCost.add(safeBigDecimal(snapshot.getModelCost()));
            summary.toolCost = summary.toolCost.add(safeBigDecimal(snapshot.getToolCost()));
            if (summary.latestSnapshotDate == null || snapshot.getSnapshotDate().isAfter(summary.latestSnapshotDate)) {
                summary.latestSnapshotDate = snapshot.getSnapshotDate();
            }
        }
        return summary;
    }

    private List<Map<String, Object>> buildDailyTrends(List<AgentRunAnalysisSnapshot> snapshots) {
        Map<LocalDate, AgentRunAnalysisSnapshot> snapshotMap = new HashMap<>();
        for (AgentRunAnalysisSnapshot snapshot : snapshots) {
            snapshotMap.put(snapshot.getSnapshotDate(), snapshot);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            AgentRunAnalysisSnapshot snapshot = snapshotMap.get(date);
            long runCount = snapshot == null ? 0L : safeLong(snapshot.getRunCount());
            long successCount = snapshot == null ? 0L : safeLong(snapshot.getSuccessCount());
            long failureCount = snapshot == null ? 0L : safeLong(snapshot.getFailureCount());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", date.toString());
            row.put("label", date.getMonthValue() + "/" + date.getDayOfMonth());
            row.put("calls", runCount);
            row.put("success", successCount);
            row.put("failed", failureCount);
            result.add(row);
        }
        return result;
    }

    private List<Map<String, Object>> getHotAgents(List<Agent> agents) {
        return agents.stream()
            .sorted(Comparator.comparingLong(Agent::getCallCount).reversed())
            .limit(5)
            .map(item -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", item.getName());
                row.put("callCount", item.getCallCount());
                return row;
            })
            .toList();
    }

    private String serializeHotAgents() {
        try {
            return objectMapper.writeValueAsString(getHotAgents(agentRepository.findAll()));
        } catch (Exception e) {
            return "[]";
        }
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal safeBigDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static class SnapshotSummary {
        long runCount;
        long successCount;
        long failureCount;
        long durationTotalMs;
        long tokenUsage;
        BigDecimal modelCost = BigDecimal.ZERO;
        BigDecimal toolCost = BigDecimal.ZERO;
        LocalDate latestSnapshotDate;
    }
}
