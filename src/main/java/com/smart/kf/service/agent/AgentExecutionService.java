package com.smart.kf.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.kf.agent.engine.AgentContext;
import com.smart.kf.agent.engine.AgentStep;
import com.smart.kf.agent.engine.ReActEngine;
import com.smart.kf.model.agent.Agent;
import com.smart.kf.model.agent.AgentExecutionLog;
import com.smart.kf.repository.agent.AgentExecutionLogRepository;
import com.smart.kf.repository.agent.AgentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(AgentExecutionService.class);

    private final ReActEngine reActEngine;
    private final AgentRepository agentRepository;
    private final AgentExecutionLogRepository logRepository;
    private final ObjectMapper objectMapper;

    public AgentExecutionService(
        ReActEngine reActEngine,
        AgentRepository agentRepository,
        AgentExecutionLogRepository logRepository,
        ObjectMapper objectMapper
    ) {
        this.reActEngine = reActEngine;
        this.agentRepository = agentRepository;
        this.logRepository = logRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> chat(String agentId, String query, List<Map<String, String>> history,
                                     Map<String, Object> debugOverrides, String username) {
        Agent agent = agentRepository.findByAgentId(agentId)
            .orElseThrow(() -> new IllegalArgumentException("Agent不存在: " + agentId));

        long startTime = System.currentTimeMillis();

        AgentContext ctx = reActEngine.execute(agent, query, history, debugOverrides, null);

        long duration = System.currentTimeMillis() - startTime;

        updateAgentStats(agent, ctx, duration);

        saveExecutionLog(ctx, agentId, query, username, duration);

        return buildResponse(ctx, duration);
    }

    public Map<String, Object> chat(String agentId, String query) {
        return chat(agentId, query, null, null, "system");
    }

    public AgentExecutionLog getExecutionLog(String executionId) {
        return logRepository.findByExecutionId(executionId)
            .orElseThrow(() -> new IllegalArgumentException("执行记录不存在: " + executionId));
    }

    public Page<AgentExecutionLog> listExecutionLogs(String agentId, int page, int size) {
        return logRepository.findByAgentIdOrderByStartedAtDesc(
            agentId,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"))
        );
    }

    private void updateAgentStats(Agent agent, AgentContext ctx, long duration) {
        long oldCalls = agent.getCallCount();
        agent.setCallCount(oldCalls + 1);
        if (ctx.getFinalAnswer() != null && !ctx.getFinalAnswer().startsWith("执行过程中出错")) {
            agent.setSuccessCount(agent.getSuccessCount() + 1);
        } else {
            agent.setFailureCount(agent.getFailureCount() + 1);
        }
        agent.setAvgDurationMs(Math.round(
            (agent.getAvgDurationMs() * oldCalls + duration) * 1.0 / agent.getCallCount()
        ));
        agentRepository.save(agent);
    }

    private void saveExecutionLog(AgentContext ctx, String agentId, String query,
                                   String username, long duration) {
        try {
            AgentExecutionLog log = new AgentExecutionLog();
            log.setExecutionId(ctx.getExecutionId());
            log.setAgentId(agentId);
            log.setTriggerType("conversation");
            log.setStatus(ctx.getFinalAnswer() != null && !ctx.getFinalAnswer().startsWith("执行过程中出错")
                ? "success" : "failed");
            log.setStartedBy(username);
            log.setIterations(ctx.getIterations());
            log.setDurationMs(duration);
            log.setPromptTokens(ctx.getTokenUsage().getPromptTokens());
            log.setCompletionTokens(ctx.getTokenUsage().getCompletionTokens());
            log.setTotalTokens(ctx.getTokenUsage().getTotalTokens());
            log.setCost(BigDecimal.valueOf(ctx.getTokenUsage().getCost()));
            log.setCompletedAt(LocalDateTime.now());

            try {
                log.setInputJson(objectMapper.writeValueAsString(Map.of("query", query)));
                log.setOutputJson(objectMapper.writeValueAsString(Map.of("answer", ctx.getFinalAnswer())));
                List<Map<String, Object>> traceList = new ArrayList<>();
                for (AgentStep step : ctx.getSteps()) {
                    Map<String, Object> t = new HashMap<>();
                    t.put("iteration", step.iteration());
                    t.put("thought", step.thought());
                    t.put("action", step.action());
                    t.put("actionInput", step.actionInput());
                    t.put("observation", step.observation());
                    t.put("durationMs", step.durationMs());
                    t.put("status", step.status());
                    traceList.add(t);
                }
                log.setTraceJson(objectMapper.writeValueAsString(traceList));
            } catch (Exception ignored) {}

            logRepository.save(log);
        } catch (Exception e) {
            logger.warn("保存Agent执行日志失败: {}", e.getMessage());
        }
    }

    private Map<String, Object> buildResponse(AgentContext ctx, long duration) {
        Map<String, Object> result = new HashMap<>();
        result.put("executionId", ctx.getExecutionId());
        result.put("answer", ctx.getFinalAnswer());
        result.put("iterations", ctx.getIterations());
        result.put("durationMs", duration);
        result.put("success", ctx.getFinalAnswer() != null && !ctx.getFinalAnswer().startsWith("执行过程中出错"));

        List<Map<String, Object>> traceList = new ArrayList<>();
        for (AgentStep step : ctx.getSteps()) {
            Map<String, Object> t = new HashMap<>();
            t.put("iteration", step.iteration());
            t.put("thought", step.thought());
            t.put("action", step.action());
            t.put("actionInput", step.actionInput());
            t.put("observation", step.observation());
            t.put("durationMs", step.durationMs());
            t.put("status", step.status());
            traceList.add(t);
        }
        result.put("trace", traceList);

        result.put("tokens", Map.of(
            "promptTokens", ctx.getTokenUsage().getPromptTokens(),
            "completionTokens", ctx.getTokenUsage().getCompletionTokens(),
            "totalTokens", ctx.getTokenUsage().getTotalTokens(),
            "cost", ctx.getTokenUsage().getCost()
        ));

        return result;
    }
}
