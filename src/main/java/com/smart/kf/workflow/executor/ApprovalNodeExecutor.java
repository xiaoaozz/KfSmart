package com.smart.kf.workflow.executor;

import com.smart.kf.workflow.engine.ExecutionContext;
import com.smart.kf.workflow.engine.NodeExecutionResult;
import com.smart.kf.workflow.engine.NodeExecutor;
import com.smart.kf.workflow.model.WorkflowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 审批节点执行器。
 * 当前实现为同步自动审批（autoApprove=true 时直接通过）。
 * 完整实现需要对接审批系统/IM 通知，此处预留接口。
 */
@Component
public class ApprovalNodeExecutor implements NodeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ApprovalNodeExecutor.class);

    @Override
    public String getNodeType() {
        return "审批";
    }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, ExecutionContext ctx) {
        String approvers = node.configString("approvers");
        String approvalType = node.configString("approvalType");
        boolean autoApprove = Boolean.TRUE.equals(node.configObject("autoApprove"));

        logger.info("审批节点: approvers={}, type={}, autoApprove={}", approvers, approvalType, autoApprove);

        if (autoApprove) {
            Map<String, Object> outputs = Map.of(
                "approved", true,
                "approver", "auto",
                "status", "approved"
            );
            ctx.setVariable("_approval_result", outputs);
            return NodeExecutionResult.of(outputs, "自动审批通过（autoApprove=true）");
        }

        // 实际场景中此处应创建审批任务并阻塞等待
        // 当前实现：记录审批信息并自动通过
        String safeApprovers = approvers != null ? approvers : "未配置";
        Map<String, Object> outputs = Map.of(
            "approved", true,
            "approver", safeApprovers,
            "status", "auto_approved",
            "message", "审批节点暂未接入实际审批系统，已自动通过"
        );
        ctx.setVariable("_approval_result", outputs);
        return NodeExecutionResult.of(outputs, "审批节点暂未接入实际系统，已自动通过。审批人: " + safeApprovers);
    }
}
