export interface WorkflowNode {
  id: string;
  type: string;
  name: string;
  x: number;
  y: number;
  description?: string;
  config?: Record<string, any>;
}

export interface WorkflowEdge {
  id?: string;
  source: string;
  target: string;
  sourcePort?: string;
  label?: string;
  condition?: string;
}

export interface NodeTraceItem {
  nodeId?: string;
  name: string;
  type?: string;
  durationMs: number;
  status: string;
  errorMessage?: string;
  description?: string;
}

export interface DebugResult {
  executionId?: string;
  trace: NodeTraceItem[];
  variables: Record<string, any>;
  tokens: {
    promptTokens: number;
    completionTokens: number;
    totalTokens: number;
    cost: number;
  };
  output: {
    answer: string;
    documents: any[];
    toolResult?: any;
  };
  durationMs: number;
  success: boolean;
  errorMessage?: string;
}

export interface WorkflowVersion {
  id: number;
  versionId: string;
  workflowId: string;
  versionNumber: number;
  name: string;
  description: string;
  status: string;
  changeDescription: string;
  snapshotBy: string;
  isActive: boolean;
  snapshotAt: string;
}

export interface WorkflowExecutionLog {
  id: number;
  executionId: string;
  workflowId: string;
  triggerType: string;
  status: string;
  startedBy: string;
  startedAt: string;
  completedAt: string | null;
  durationMs: number;
  totalTokens: number;
  cost: number;
  errorMessage: string | null;
}
