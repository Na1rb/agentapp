package com.nairb.ai130.app.workflow.executor;

import com.nairb.ai130.app.PromptTemplateService;
import com.nairb.ai130.domain.workflow.NodeResult;
import com.nairb.ai130.domain.workflow.WorkflowDefinition;
import com.nairb.ai130.domain.workflow.WorkflowExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

/**
 * DECOMPOSE 节点执行器 — 将分析结果拆解为可并行执行的子任务。
 * <p>
 * 输入：ANALYSIS 节点的输出
 * 输出：包含子任务列表的文本（下一阶段 PARALLEL_SPLIT 会从中提取任务）
 */
@Component
public class DecomposeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(DecomposeExecutor.class);

    private final ChatClient primaryClient;
    private final PromptTemplateService promptService;

    public DecomposeExecutor(@Qualifier("chatClient") ChatClient primaryClient, PromptTemplateService promptService) {
        this.primaryClient = primaryClient;
        this.promptService = promptService;
    }

    @Override
    public Flux<NodeResult> execute(WorkflowExecution execution,
                                    WorkflowDefinition.NodeDef node,
                                    WorkflowDefinition definition,
                                    List<NodeResult> prevResults,
                                    String prompt) {
        String sessionId = execution.getSessionId();
        String analysisResult = extractLastOutput(prevResults);
        String systemText = promptService.getPrompt(null) + "\n\n" + buildDecomposePrompt();

        log.info("[{}] DECOMPOSE node executing, analysisLen={}", sessionId, analysisResult.length());

        String userInput = "原始请求：" + prompt + "\n\n分析结果：\n" + analysisResult;

        return primaryClient.prompt()
                .system(s -> s.text(systemText))
                .user(userInput)
                .options(OpenAiChatOptions.builder()
                        .model("qwen-plus")
                        .temperature(0.7)
                        .build())
                .stream()
                .content()
                .reduce("", (a, b) -> a + b)
                .flatMapMany(fullResponse -> {
                    NodeResult result = NodeResult.pending(
                            UUID.randomUUID().toString(),
                            execution.getId(),
                            node.getId(),
                            userInput);
                    result.markRunning();
                    result.markCompleted(fullResponse);
                    log.info("[{}] DECOMPOSE complete, outputLen={}", sessionId, fullResponse.length());
                    return Flux.just(result);
                });
    }

    private String buildDecomposePrompt() {
        return """
                [任务拆解模式]
                请将上述用户请求拆解为可独立执行的子任务。

                要求：
                1. 每个子任务应为原子操作（一次工具调用或一个简单推理）
                2. 子任务之间应尽量独立，以便并行执行
                3. 每个子任务包含明确的执行描述
                4. 最后应包含一个"综合汇总"子任务

                请严格按以下 JSON 格式输出（不要包含其他文字）：
                ```json
                {
                  "tasks": [
                    {"id": "1", "desc": "查询杭州明天天气"},
                    {"id": "2", "desc": "查询北京明天天气"},
                    {"id": "3", "desc": "比较两地天气并给出建议"}
                  ]
                }
                ```
                """;
    }

    private String extractLastOutput(List<NodeResult> results) {
        if (results == null || results.isEmpty()) return "";
        NodeResult last = results.get(results.size() - 1);
        return last.getOutputText() != null ? last.getOutputText() : "";
    }
}
