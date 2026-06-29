package com.smart.kf.service.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.kf.model.workflow.Workflow;
import com.smart.kf.repository.workflow.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

    @Mock private WorkflowRepository workflowRepository;
    @Mock private WorkflowExecutionService executionService;
    @Mock private WorkflowVersionService versionService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private WorkflowService workflowService;

    private Workflow wf1;
    private Workflow wf2;

    @BeforeEach
    void setUp() {
        // inject real objectMapper since @InjectMocks doesn't handle it with ObjectMapper mock easily
        // WorkflowService receives ObjectMapper via constructor — use real ObjectMapper
        workflowService = new WorkflowService(workflowRepository, executionService, versionService, objectMapper);

        wf1 = new Workflow();
        wf1.setId(1L);
        wf1.setWorkflowId("wf_001");
        wf1.setName("Invoice Flow");
        wf1.setStatus("published");
        wf1.setCallCount(60L);
        wf1.setSuccessCount(55L);
        wf1.setFailureCount(5L);
        wf1.setAvgDurationMs(300L);
        wf1.setUpdatedAt(LocalDateTime.now());

        wf2 = new Workflow();
        wf2.setId(2L);
        wf2.setWorkflowId("wf_002");
        wf2.setName("Approval Flow");
        wf2.setStatus("draft");
        wf2.setCallCount(20L);
        wf2.setSuccessCount(18L);
        wf2.setFailureCount(2L);
        wf2.setAvgDurationMs(800L);
        wf2.setUpdatedAt(LocalDateTime.now().minusDays(1));
    }

    // ---- workflowStats ----

    @Test
    void workflowStats_emptyList_returns100SuccessRate() {
        when(workflowRepository.findAll()).thenReturn(new ArrayList<>());

        Map<String, Object> stats = workflowService.workflowStats();

        assertEquals(0L, stats.get("workflowCount"));
        assertEquals(0L, stats.get("runCount"));
        assertEquals(100L, stats.get("successRate"));
        assertEquals(0L, stats.get("avgDurationMs"));
    }

    @Test
    void workflowStats_withWorkflows_calculatesCorrectly() {
        when(workflowRepository.findAll()).thenReturn(new ArrayList<>(List.of(wf1, wf2)));

        Map<String, Object> stats = workflowService.workflowStats();

        assertEquals(2L, stats.get("workflowCount"));
        assertEquals(80L, stats.get("runCount")); // 60 + 20
        long expectedRate = Math.round((55 + 18) * 100.0 / 80); // ~91%
        assertEquals(expectedRate, stats.get("successRate"));
    }

    @Test
    void workflowStats_nullCounts_treatedAsZero() {
        Workflow wf = new Workflow();
        wf.setWorkflowId("wf_x");
        when(workflowRepository.findAll()).thenReturn(new ArrayList<>(List.of(wf)));

        Map<String, Object> stats = workflowService.workflowStats();

        assertEquals(0L, stats.get("runCount"));
        assertEquals(100L, stats.get("successRate"));
    }

    // ---- listWorkflows ----

    @Test
    void listWorkflows_noKeyword_returnsAllSortedByUpdatedAt() {
        when(workflowRepository.findAll()).thenReturn(new ArrayList<>(List.of(wf2, wf1)));

        var result = workflowService.listWorkflows(null, null, com.smart.kf.utils.pagination.PageQuery.of(1, 10, null));

        assertEquals(2, result.total());
        assertEquals("wf_001", result.records().get(0).getWorkflowId()); // newer first
    }

    @Test
    void listWorkflows_withKeyword_delegatesToFilteredQuery() {
        when(workflowRepository.findByNameContainingIgnoreCase("Invoice")).thenReturn(new ArrayList<>(List.of(wf1)));

        var result = workflowService.listWorkflows("Invoice", null, com.smart.kf.utils.pagination.PageQuery.of(1, 10, null));

        assertEquals(1, result.total());
        verify(workflowRepository, never()).findAll();
    }

    @Test
    void listWorkflows_withStatusFilter_returnsOnlyMatching() {
        when(workflowRepository.findAll()).thenReturn(new ArrayList<>(List.of(wf1, wf2))); // published + draft

        var result = workflowService.listWorkflows(null, "published", com.smart.kf.utils.pagination.PageQuery.of(1, 10, null));

        assertEquals(1, result.total());
        assertEquals("wf_001", result.records().get(0).getWorkflowId());
    }

    // ---- getWorkflow ----

    @Test
    void getWorkflow_byNumericId_returnsWorkflow() {
        when(workflowRepository.findById(1L)).thenReturn(Optional.of(wf1));

        Workflow result = workflowService.getWorkflow("1");

        assertEquals("wf_001", result.getWorkflowId());
    }

    @Test
    void getWorkflow_byStringId_returnsWorkflow() {
        when(workflowRepository.findByWorkflowId("wf_001")).thenReturn(Optional.of(wf1));

        Workflow result = workflowService.getWorkflow("wf_001");

        assertEquals("wf_001", result.getWorkflowId());
    }

    @Test
    void getWorkflow_notFound_throwsException() {
        when(workflowRepository.findByWorkflowId("wf_x")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> workflowService.getWorkflow("wf_x"));
    }

    // ---- saveWorkflow ----

    @Test
    void saveWorkflow_newWorkflow_assignsWorkflowId() {
        Workflow request = new Workflow();
        request.setName("New Flow");
        request.setStatus("draft");

        when(workflowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Workflow result = workflowService.saveWorkflow(request);

        assertNotNull(result.getWorkflowId());
        assertTrue(result.getWorkflowId().startsWith("wf_"));
        verify(versionService, never()).createVersion(any(), any(), any());
    }

    @Test
    void saveWorkflow_existingWorkflow_createsVersion() {
        Workflow request = new Workflow();
        request.setWorkflowId("wf_001");
        request.setName("Updated Flow");
        request.setStatus("published");

        when(workflowRepository.findByWorkflowId("wf_001")).thenReturn(Optional.of(wf1));
        when(workflowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        workflowService.saveWorkflow(request);

        verify(versionService).createVersion(any(), eq("system"), eq("编辑保存"));
    }

    // ---- publishWorkflow ----

    @Test
    void publishWorkflow_setsStatusAndTimestamp() {
        wf2.setStatus("draft");
        when(workflowRepository.findByWorkflowId("wf_002")).thenReturn(Optional.of(wf2));
        when(workflowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Workflow result = workflowService.publishWorkflow("wf_002");

        assertEquals("published", result.getStatus());
        assertNotNull(result.getPublishedAt());
    }

    // ---- disableWorkflow ----

    @Test
    void disableWorkflow_setsStatusToDisabled() {
        when(workflowRepository.findByWorkflowId("wf_001")).thenReturn(Optional.of(wf1));
        when(workflowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Workflow result = workflowService.disableWorkflow("wf_001");

        assertEquals("disabled", result.getStatus());
    }

    // ---- copyWorkflow ----

    @Test
    void copyWorkflow_createsNewWorkflowWithCopySuffix() {
        when(workflowRepository.findByWorkflowId("wf_001")).thenReturn(Optional.of(wf1));
        when(workflowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Workflow copy = workflowService.copyWorkflow("wf_001");

        assertTrue(copy.getName().contains("副本"));
        assertNotEquals("wf_001", copy.getWorkflowId());
        assertTrue(copy.getWorkflowId().startsWith("wf_"));
        assertEquals("draft", copy.getStatus());
        assertNull(copy.getPublishedAt());
        verify(workflowRepository, times(2)).save(any()); // source installCount + copy
    }

    // ---- deleteWorkflow ----

    @Test
    void deleteWorkflow_found_callsDelete() {
        when(workflowRepository.findByWorkflowId("wf_001")).thenReturn(Optional.of(wf1));

        workflowService.deleteWorkflow("wf_001");

        verify(workflowRepository).delete(wf1);
    }

    @Test
    void deleteWorkflow_notFound_throwsException() {
        when(workflowRepository.findByWorkflowId("wf_x")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> workflowService.deleteWorkflow("wf_x"));
    }

    // ---- saveGraph ----

    @Test
    void saveGraph_validJson_savesNodesAndEdges() throws Exception {
        when(workflowRepository.findById(1L)).thenReturn(Optional.of(wf1));
        when(workflowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = Map.of(
            "nodes", List.of(Map.of("id", "n1", "type", "start")),
            "edges", List.of()
        );

        Workflow result = workflowService.saveGraph("1", body);

        assertNotNull(result.getNodesJson());
        assertNotNull(result.getEdgesJson());
        assertTrue(result.getNodesJson().contains("n1"));
    }
}
