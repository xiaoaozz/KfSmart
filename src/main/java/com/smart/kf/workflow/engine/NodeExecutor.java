package com.smart.kf.workflow.engine;

import com.smart.kf.workflow.model.WorkflowNode;

public interface NodeExecutor {

    String getNodeType();

    NodeExecutionResult execute(WorkflowNode node, ExecutionContext context);
}
