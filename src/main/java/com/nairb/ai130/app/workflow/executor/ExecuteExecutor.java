package com.nairb.ai130.app.workflow.executor;

import com.nairb.ai130.app.PromptTemplateService;
import com.nairb.ai130.app.service.McpToolLoader;
import com.nairb.ai130.domain.workflow.NodeResult;
import com.nairb.ai130.domain.workflow.WorkflowDefinition;
import com.nairb.ai130.domain.workflow.WorkflowExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * EXECUTE 节点执行器 — 执行一个具体的子任务。
 * <p>
 * PARALLEL_SPLIT 会为每个子任务创建一个独立的 EXECUTE 节点实例。
 * 支持 MCP 工具注入。
 */
@Component
public class ExecuteExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(ExecuteExecutor.class);

    private final ChatClient primaryClient;
    private final ChatClient fallbackClient;
    private final McpToolLoader mcpToolLoader;
    private final PromptTemplateService promptService;

    public ExecuteExecutor(@Qualifier("chatClient") ChatClient primaryClient,
                           @Qualifier("deepseekChatClient") ChatClient fallbackClient,
                           McpToolLoader mcpToolLoader,
                           PromptTemplateService promptService) {
        this.primaryClient = primaryClient;
        this.fallbackClient = fallbackClient;
        this.mcpToolLoader = mcpToolLoader;
        this.promptService = promptService;
    }

    /**
     * 接收子任务描述和工具列表作为 configJson。
     * configJson 格式：{"taskDesc":"...", "toolNames":["..."]}
     */
    @Override
    public Flux<NodeResult> execute(WorkflowExecution execution,
                                    WorkflowDefinition.NodeDef node,
                                    WorkflowDefinition definition,
                                    List<NodeResult> prevResults,
                                    String prompt) {
        String sessionId = execution.getSessionId();

        // 从 node.configJson 提取子任务描述和工具
        String taskDesc = node.getConfigJson();
        if (taskDesc == null || taskDesc.isBlank()) {
            taskDesc = "综合处理用户请求：" + prompt;
        }

        // 加载 MCP 工具
        final String resolvedTaskDesc = taskDesc;
        List<FunctionToolCallback<String, String>> tools = loadToolsFromConfig(node);
        String systemText = promptService.getPrompt(null) + "\n\n" + buildExecutePrompt(resolvedTaskDesc, prevResults);

        log.info("[{}] EXECUTE node executing, task={}, tools={}",
                sessionId, truncate(resolvedTaskDesc, 100), tools.size());

        Flux<String> modelStream = streamByModel(sessionId, systemText, prompt, tools);

        return modelStream
                .reduce("", (a, b) -> a + b)
                .flatMapMany(fullResponse -> {
                    NodeResult result = NodeResult.pending(
                            UUID.randomUUID().toString(),
                            execution.getId(),
                            node.getId(),
                            resolvedTaskDesc);
                    result.markRunning();
                    result.markCompleted(fullResponse);
                    log.info("[{}] EXECUTE complete, outputLen={}", sessionId, fullResponse.length());
                    return Flux.just(result);
                });
    }

    private String buildExecutePrompt(String taskDesc, List<NodeResult> prevResults) {
        StringBuilder sb = new StringBuilder();
        sb.append("[任务执行模式]\n");
        sb.append("请执行以下任务：").append(taskDesc).append("\n\n");

        // 提供上下文：已完成的结果
        if (prevResults != null && !prevResults.isEmpty()) {
            sb.append("已完成的分析参考：\n");
            for (NodeResult r : prevResults) {
                String out = r.getOutputText();
                if (out != null && !out.isBlank()) {
                    sb.append("---\n").append(truncate(out, 500)).append("\n");
                }
            }
        }

        sb.append("\n如果有可用的工具，请使用工具获取数据。直接给出执行结果。");
        return sb.toString();
    }

    private List<FunctionToolCallback<String, String>> loadToolsFromConfig(WorkflowDefinition.NodeDef node) {
        String config = node.getConfigJson();
        if (config == null || config.isBlank()) return Collections.emptyList();
        try {
            // 简单解析工具名称列表
            return mcpToolLoader.loadTools(List.of(config.split(",")));
        } catch (Exception e) {
            log.warn("Failed to load tools from config: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private Flux<String> streamByModel(String sessionId, String systemText, String prompt,
                                        List<FunctionToolCallback<String, String>> tools) {
        var spec = primaryClient.prompt()
                .system(s -> s.text(systemText))
                .user(prompt)
                .options(OpenAiChatOptions.builder().model("qwen-plus").temperature(0.7).build())
                .advisors(a -> a.param("conversation_id", sessionId).param("retrieve_size", 10));

        if (tools != null && !tools.isEmpty()) {
            spec = spec.tools(tools.toArray(new FunctionToolCallback[0]));
        }

        return spec.stream().content()
                .onErrorResume(e -> {
                    log.warn("[{}] Primary model failed: {}, falling back", sessionId, e.getMessage());
                    return fallbackClient.prompt()
                            .system(s -> s.text(systemText))
                            .user(prompt)
                            .options(OpenAiChatOptions.builder().model("deepseek-v4-flash").temperature(0.7).build())
                            .stream().content();
                });
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
