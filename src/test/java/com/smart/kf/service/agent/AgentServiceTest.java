package com.smart.kf.service.agent;

import com.smart.kf.model.agent.Agent;
import com.smart.kf.model.agent.AgentI18n;
import com.smart.kf.repository.agent.AgentI18nRepository;
import com.smart.kf.repository.agent.AgentRepository;
import com.smart.kf.service.I18nTranslationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock private AgentRepository agentRepository;
    @Mock private AgentVersionService versionService;
    @Mock private AgentRunAnalysisService runAnalysisService;
    @Mock private AgentI18nRepository agentI18nRepository;
    @Mock private I18nTranslationService i18nTranslationService;

    @InjectMocks
    private AgentService agentService;

    private Agent agent1;
    private Agent agent2;

    @BeforeEach
    void setUp() {
        // i18nTranslationService is @Autowired (not constructor-injected) so inject manually
        ReflectionTestUtils.setField(agentService, "i18nTranslationService", i18nTranslationService);

        agent1 = new Agent();
        agent1.setAgentId("agt_001");
        agent1.setName("Customer Support");
        agent1.setStatus("published");
        agent1.setCallCount(100L);
        agent1.setSuccessCount(95L);
        agent1.setFailureCount(5L);
        agent1.setAvgDurationMs(200L);
        agent1.setUpdatedAt(LocalDateTime.now());

        agent2 = new Agent();
        agent2.setAgentId("agt_002");
        agent2.setName("Data Analyst");
        agent2.setStatus("draft");
        agent2.setCallCount(50L);
        agent2.setSuccessCount(40L);
        agent2.setFailureCount(10L);
        agent2.setAvgDurationMs(500L);
        agent2.setUpdatedAt(LocalDateTime.now().minusDays(1));
    }

    // ---- agentStats ----

    @Test
    void agentStats_emptyList_returns100SuccessRate() {
        when(agentRepository.findAll()).thenReturn(new ArrayList<>());

        Map<String, Object> stats = agentService.agentStats();

        assertEquals(0L, stats.get("agentCount"));
        assertEquals(0L, stats.get("runCount"));
        assertEquals(100L, stats.get("successRate"));
        assertEquals(0L, stats.get("avgDurationMs"));
    }

    @Test
    void agentStats_withAgents_calculatesCorrectly() {
        when(agentRepository.findAll()).thenReturn(new ArrayList<>(List.of(agent1, agent2)));

        Map<String, Object> stats = agentService.agentStats();

        assertEquals(2L, stats.get("agentCount"));
        assertEquals(150L, stats.get("runCount")); // 100 + 50
        long expectedRate = Math.round((95 + 40) * 100.0 / 150); // ~90%
        assertEquals(expectedRate, stats.get("successRate"));
    }

    @Test
    void agentStats_nullCallCount_treatedAsZero() {
        Agent a = new Agent();
        a.setAgentId("agt_x");
        a.setName("NoStats");
        a.setStatus("draft");
        // all counts null
        when(agentRepository.findAll()).thenReturn(new ArrayList<>(List.of(a)));

        Map<String, Object> stats = agentService.agentStats();

        assertEquals(0L, stats.get("runCount"));
        assertEquals(100L, stats.get("successRate")); // no calls → 100%
    }

    // ---- listAgents ----

    @Test
    void listAgents_noKeyword_returnsAllSortedByUpdatedAt() {
        when(agentRepository.findAll()).thenReturn(new ArrayList<>(List.of(agent2, agent1))); // older first intentionally

        var result = agentService.listAgents(null, com.smart.kf.utils.pagination.PageQuery.of(1, 10, null));

        assertEquals(2, result.total());
        // agent1 is newer, should come first after sort
        assertEquals("agt_001", result.records().get(0).getAgentId());
    }

    @Test
    void listAgents_withKeyword_delegatesToFilteredQuery() {
        when(agentRepository.findByNameContainingIgnoreCase("Support")).thenReturn(new ArrayList<>(List.of(agent1)));

        var result = agentService.listAgents("Support", com.smart.kf.utils.pagination.PageQuery.of(1, 10, null));

        assertEquals(1, result.total());
        verify(agentRepository, never()).findAll();
    }

    @Test
    void listAgents_emptyKeyword_delegatesToFindAll() {
        when(agentRepository.findAll()).thenReturn(new ArrayList<>(List.of(agent1)));

        agentService.listAgents("", com.smart.kf.utils.pagination.PageQuery.of(1, 10, null));

        verify(agentRepository).findAll();
        verify(agentRepository, never()).findByNameContainingIgnoreCase(any());
    }

    // ---- getAgent ----

    @Test
    void getAgent_found_returnsAgent() {
        when(agentRepository.findByAgentId("agt_001")).thenReturn(Optional.of(agent1));

        Agent result = agentService.getAgent("agt_001");

        assertEquals("agt_001", result.getAgentId());
    }

    @Test
    void getAgent_notFound_throwsException() {
        when(agentRepository.findByAgentId("agt_x")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> agentService.getAgent("agt_x"));
    }

    // ---- saveAgent ----

    @Test
    void saveAgent_newAgent_assignsAgentIdAndTranslates() {
        Agent request = new Agent();
        request.setName("New Bot");
        request.setStatus("draft");
        // no agentId → new agent

        when(agentRepository.save(any(Agent.class))).thenAnswer(inv -> inv.getArgument(0));

        Agent result = agentService.saveAgent(request);

        assertNotNull(result.getAgentId());
        assertTrue(result.getAgentId().startsWith("agt_"));
        verify(i18nTranslationService).translateAgentAsync(anyString(), eq("New Bot"), any());
        verify(versionService, never()).createVersion(any(), any(), any());
    }

    @Test
    void saveAgent_existingAgent_createsVersionAndRetranslates() {
        Agent request = new Agent();
        request.setAgentId("agt_001");
        request.setName("Updated Name");
        request.setStatus("published");

        when(agentRepository.findByAgentId("agt_001")).thenReturn(Optional.of(agent1));
        when(agentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        agentService.saveAgent(request);

        verify(versionService).createVersion(any(), eq("system"), eq("编辑保存"));
        verify(i18nTranslationService).retranslateAgentAsync(eq("agt_001"), any(), any());
    }

    // ---- publishAgent ----

    @Test
    void publishAgent_setsStatusPublishedAndTimestamp() {
        agent1.setStatus("draft");
        when(agentRepository.findByAgentId("agt_001")).thenReturn(Optional.of(agent1));
        when(agentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Agent result = agentService.publishAgent("agt_001");

        assertEquals("published", result.getStatus());
        assertNotNull(result.getPublishedAt());
    }

    // ---- copyAgent ----

    @Test
    void copyAgent_createsNewAgentWithCopySuffix() {
        when(agentRepository.findByAgentId("agt_001")).thenReturn(Optional.of(agent1));
        when(agentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Agent copy = agentService.copyAgent("agt_001");

        assertTrue(copy.getName().contains("副本"));
        assertNotEquals("agt_001", copy.getAgentId());
        assertTrue(copy.getAgentId().startsWith("agt_"));
        assertEquals("draft", copy.getStatus());
        assertNull(copy.getPublishedAt());
        verify(agentRepository, times(2)).save(any()); // source increment + copy save
    }

    // ---- deleteAgent ----

    @Test
    void deleteAgent_found_callsDelete() {
        when(agentRepository.findByAgentId("agt_001")).thenReturn(Optional.of(agent1));

        agentService.deleteAgent("agt_001");

        verify(agentRepository).delete(agent1);
    }

    @Test
    void deleteAgent_notFound_throwsException() {
        when(agentRepository.findByAgentId("agt_x")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> agentService.deleteAgent("agt_x"));
    }

    // ---- marketplace ----

    @Test
    void marketplace_returnsOnlyPublishedAgents() {
        when(agentRepository.findAll()).thenReturn(new ArrayList<>(List.of(agent1, agent2)));
        // agent1 is published, agent2 is draft

        List<Map<String, Object>> result = agentService.marketplace();

        assertEquals(1, result.size());
        assertEquals("agt_001", result.get(0).get("agentId"));
    }

    @Test
    void marketplace_emptyList_returnsEmpty() {
        when(agentRepository.findAll()).thenReturn(new ArrayList<>());

        List<Map<String, Object>> result = agentService.marketplace();

        assertTrue(result.isEmpty());
    }

    // ---- upsertAgentI18n ----

    @Test
    void upsertAgentI18n_newRecord_savesNew() {
        when(agentI18nRepository.findByAgentIdAndLang("agt_001", "en-US")).thenReturn(Optional.empty());
        when(agentI18nRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AgentI18n result = agentService.upsertAgentI18n("agt_001", "en-US", "Customer Support", "Handles queries");

        assertEquals("agt_001", result.getAgentId());
        assertEquals("en-US", result.getLang());
        assertEquals("Customer Support", result.getName());
    }

    @Test
    void upsertAgentI18n_existingRecord_updates() {
        AgentI18n existing = new AgentI18n();
        existing.setAgentId("agt_001");
        existing.setLang("en-US");
        existing.setName("Old Name");
        when(agentI18nRepository.findByAgentIdAndLang("agt_001", "en-US")).thenReturn(Optional.of(existing));
        when(agentI18nRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AgentI18n result = agentService.upsertAgentI18n("agt_001", "en-US", "New Name", null);

        assertEquals("New Name", result.getName());
    }
}
