package com.smart.kf.workflow.executor;

import com.smart.kf.workflow.engine.ExecutionContext;
import com.smart.kf.workflow.engine.NodeExecutionResult;
import com.smart.kf.workflow.engine.NodeExecutor;
import com.smart.kf.workflow.model.WorkflowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 工作流「代码执行」节点。
 *
 * <p><b>安全策略（止血阶段）</b>：原实现使用 {@code javax.script.ScriptEngine.eval(code)}
 * 无任何沙箱执行工作流作者提供的任意 JavaScript，可通过 {@code Java.type} 逃逸到 JVM，
 * 构成 RCE（且 Java 17 已移除 Nashorn，原实现实际惰性失效）。鉴于任何登录用户均可创建
 * 含代码节点的工作流（{@code agent:write} 权限），该节点当前<b>默认禁用</b>。
 *
 * <p>启用需显式配置 {@code workflow.code-node.enabled=true}；但在引入沙箱前，
 * 启用仅返回受限提示，不执行任意代码，避免恢复 RCE 风险。
 *
 * <p><b>后续路径</b>（见 remediation-progress.md）：接入 GraalVM Polyglot JS
 * （{@code HostAccess.NONE}）真沙箱。曾尝试引入 {@code org.graalvm.polyglot:polyglot}，
 * 但其模块化 jar（module-info.class）与 Lombok 注解处理在单模块 Maven 构建下冲突，
 * 需以独立 Maven module 隔离代码执行引擎后再落地。
 */
@Component
public class CodeNodeExecutor implements NodeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(CodeNodeExecutor.class);

    private final boolean enabled;

    public CodeNodeExecutor(@Value("${workflow.code-node.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String getNodeType() {
        return "代码执行";
    }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, ExecutionContext ctx) {
        String language = node.configString("language");
        if (language == null || language.isBlank()) {
            language = "JavaScript";
        }

        if (!enabled) {
            logger.warn("代码执行节点已被禁用（workflow.code-node.enabled=false），拒绝执行任意代码");
            Map<String, Object> outputs = new HashMap<>();
            outputs.put("result", "[代码执行节点已禁用：未启用沙箱，拒绝执行任意代码]");
            outputs.put("status", "disabled");
            return NodeExecutionResult.of(outputs, "代码执行节点已禁用");
        }

        // 启用但未接入沙箱：仍不执行任意代码，避免恢复 RCE 风险
        logger.warn("代码执行节点已启用但沙箱未接入，拒绝执行 {} 代码", language);
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("result", "[代码执行沙箱未就绪，拒绝执行 " + language + " 代码]");
        outputs.put("status", "sandbox_not_ready");
        return NodeExecutionResult.of(outputs, "代码执行沙箱未就绪");
    }
}
