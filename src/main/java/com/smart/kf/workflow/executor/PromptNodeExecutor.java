package com.smart.kf.workflow.executor;

import com.smart.kf.model.agent.PromptTemplate;
import com.smart.kf.repository.agent.PromptTemplateRepository;
import com.smart.kf.workflow.engine.ExecutionContext;
import com.smart.kf.workflow.engine.NodeExecutionResult;
import com.smart.kf.workflow.engine.NodeExecutor;
import com.smart.kf.workflow.model.WorkflowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class PromptNodeExecutor implements NodeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(PromptNodeExecutor.class);

    private final PromptTemplateRepository promptRepository;

    public PromptNodeExecutor(PromptTemplateRepository promptRepository) {
        this.promptRepository = promptRepository;
    }

    @Override
    public String getNodeType() {
        return "Prompt模板";
    }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, ExecutionContext ctx) {
        String templateId = node.configString("templateId");
        String templateContent = node.configString("templateContent");

        String content;

        if (templateId != null && !templateId.isBlank()) {
            // 从数据库加载模板
            List<PromptTemplate> templates = promptRepository.findAll();
            PromptTemplate matched = templates.stream()
                .filter(t -> t.getName().equals(templateId) || templateId.contains(t.getName()))
                .findFirst()
                .orElse(null);

            if (matched != null) {
                content = buildFromTemplate(matched, ctx);
            } else {
                logger.warn("Prompt 模板未找到: {}", templateId);
                content = ctx.resolveTemplate(templateContent);
            }
        } else {
            content = ctx.resolveTemplate(templateContent);
        }

        ctx.setVariable("llmPrompt", content);

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("output", content);
        outputs.put("llmPrompt", content);
        String source = (templateId != null && !templateId.isBlank()) ? "模板[" + templateId + "]" : "自定义内容";
        return NodeExecutionResult.of(outputs, "加载Prompt(" + source + ")，生成" + content.length() + "字");
    }

    private String buildFromTemplate(PromptTemplate template, ExecutionContext ctx) {
        StringBuilder sb = new StringBuilder();

        String systemContent = Optional.ofNullable(template.getSystemContent()).orElse("");
        if (!systemContent.isBlank()) {
            sb.append("[System]\n");
            systemContent = ctx.resolveTemplate(systemContent);
            sb.append(systemContent).append("\n\n");
        }

        String userContent = Optional.ofNullable(template.getContent()).orElse("");
        userContent = ctx.resolveTemplate(userContent);
        sb.append("[User]\n").append(userContent);

        return sb.toString();
    }
}
