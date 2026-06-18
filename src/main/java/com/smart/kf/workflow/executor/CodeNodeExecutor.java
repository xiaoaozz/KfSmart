package com.smart.kf.workflow.executor;

import com.smart.kf.workflow.engine.ExecutionContext;
import com.smart.kf.workflow.engine.NodeExecutionResult;
import com.smart.kf.workflow.engine.NodeExecutor;
import com.smart.kf.workflow.model.WorkflowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.HashMap;
import java.util.Map;

@Component
public class CodeNodeExecutor implements NodeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(CodeNodeExecutor.class);

    @Override
    public String getNodeType() {
        return "代码执行";
    }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, ExecutionContext ctx) {
        String language = node.configString("language");
        String code = node.configString("code");

        if (language == null || language.isBlank()) {
            language = "JavaScript";
        }

        if ("JavaScript".equalsIgnoreCase(language) || "JS".equalsIgnoreCase(language)) {
            return executeJavaScript(code, ctx);
        }

        logger.info("{} 代码执行暂未实现", language);
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("result", "[" + language + " 执行暂未实现]");
        outputs.put("status", "not_implemented");
        return NodeExecutionResult.of(outputs);
    }

    @SuppressWarnings("unchecked")
    private NodeExecutionResult executeJavaScript(String code, ExecutionContext ctx) {
        try {
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("js");
            if (engine == null) {
                engine = manager.getEngineByName("javascript");
            }
            if (engine == null) {
                engine = manager.getEngineByName("nashorn");
            }
            if (engine == null) {
                Map<String, Object> outputs = new HashMap<>();
                outputs.put("result", "[JavaScript 引擎不可用]");
                outputs.put("status", "engine_not_found");
                return NodeExecutionResult.of(outputs);
            }

            engine.put("input", ctx.getVariables());
            engine.put("context", ctx.getVariables());
            Object result = engine.eval(code);

            Map<String, Object> outputs = new HashMap<>();
            if (result instanceof Map) {
                outputs.putAll((Map<String, Object>) result);
            } else {
                outputs.put("result", result != null ? result : "");
            }
            return NodeExecutionResult.of(outputs, "执行JavaScript代码，返回结果: " + (result != null ? String.valueOf(result).substring(0, Math.min(80, String.valueOf(result).length())) + "..." : "空"));
        } catch (Exception e) {
            throw new RuntimeException("代码执行失败: " + e.getMessage(), e);
        }
    }
}
