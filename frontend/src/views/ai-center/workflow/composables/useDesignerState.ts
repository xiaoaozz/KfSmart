import { ref, reactive, computed } from 'vue';
import type { WorkflowNode, WorkflowEdge } from '../types/workflow';
import { defaultNodes, defaultEdges } from '../constants/nodeDefinitions';

export function useDesignerState() {
  const designer = reactive({
    workflowId: '',
    name: '',
    description: '',
    type: '',
    status: '',
    ownerName: '',
    permissionScope: '',
    tags: '',
    knowledgeBases: '',
    promptRefs: '',
    mcpTools: '',
    models: '',
    selectedNodeId: '',
    nodes: [] as WorkflowNode[],
    edges: [] as WorkflowEdge[]
  });

  const designerSnapshot = ref('');
  const hasSelectedWorkflow = computed(() => Boolean(designer.workflowId));
  const selectedNode = computed(() => designer.nodes.find(item => item.id === designer.selectedNodeId) || designer.nodes[0]);
  const designerDirty = computed(() => designerSnapshot.value !== getDesignerSnapshot());

  function getDesignerSnapshot() {
    return JSON.stringify({
      workflowId: designer.workflowId,
      name: designer.name,
      description: designer.description,
      type: designer.type,
      status: designer.status,
      ownerName: designer.ownerName,
      permissionScope: designer.permissionScope,
      tags: designer.tags,
      knowledgeBases: designer.knowledgeBases,
      promptRefs: designer.promptRefs,
      mcpTools: designer.mcpTools,
      models: designer.models,
      nodes: designer.nodes,
      edges: designer.edges
    });
  }

  function captureDesignerSnapshot() {
    designerSnapshot.value = getDesignerSnapshot();
  }

  function revertDesignerChanges() {
    if (!designerSnapshot.value) return;
    const snapshot = JSON.parse(designerSnapshot.value);
    Object.assign(designer, snapshot);
    designer.selectedNodeId = designer.nodes[0]?.id || '';
  }

  function resetDesigner() {
    designer.workflowId = '';
    designer.name = '';
    designer.description = '';
    designer.type = '';
    designer.status = '';
    designer.ownerName = '';
    designer.permissionScope = '';
    designer.tags = '';
    designer.knowledgeBases = '';
    designer.promptRefs = '';
    designer.mcpTools = '';
    designer.models = '';
    designer.nodes = [];
    designer.edges = [];
    designer.selectedNodeId = '';
    captureDesignerSnapshot();
  }

  return {
    designer,
    hasSelectedWorkflow,
    selectedNode,
    designerDirty,
    captureDesignerSnapshot,
    revertDesignerChanges,
    resetDesigner
  };
}
