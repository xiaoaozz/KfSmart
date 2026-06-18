package com.smart.kf.workflow.executor;

import com.smart.kf.workflow.engine.ExecutionContext;
import com.smart.kf.workflow.engine.NodeExecutionResult;
import com.smart.kf.workflow.engine.NodeExecutor;
import com.smart.kf.workflow.model.WorkflowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class PythonNodeExecutor implements NodeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(PythonNodeExecutor.class);

    @Override
    public String getNodeType() {
        return "Python执行";
    }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, ExecutionContext ctx) {
        logger.info("Python 执行节点暂不支持服务端代码执行，返回 stub 结果");
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("result", "[Python 执行暂未实现]");
        outputs.put("code", node.configString("code"));
        outputs.put("status", "not_implemented");
        return NodeExecutionResult.of(outputs, "Python执行暂未实现");
    }
}
