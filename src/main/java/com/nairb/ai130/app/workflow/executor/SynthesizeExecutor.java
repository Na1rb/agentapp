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
 * SYNTHESIZE 节点执行器 — 汇总全部执行结果，生成最终流式回答。
 * <p>
 * 这是唯一使用 Flux 流式输出多个 NodeResult 的执行器（每个 chunk 一个结果）。
 */
@Component
public class SynthesizeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(SynthesizeExecutor.class);

    private final ChatClient primaryClient;
    private final ChatClient fallbackClient;
    private final PromptTemplateService promptService;

    public SynthesizeExecutor(@Qualifier("chatClient") ChatClient primaryClient,
                              @Qualifier("deepseekChatClient") ChatClient fallbackClient,
                              PromptTemplateService promptService) {
        this.primaryClient = primaryClient;
        this.fallbackClient = fallbackClient;
        this.promptService = promptService;
    }

    @Override
    public Flux<NodeResult> execute(WorkflowExecution execution,
                                    WorkflowDefinition.NodeDef node,
                                    WorkflowDefinition definition,
                                    List<NodeResult> prevResults,
                                    String prompt) {
        String sessionId = execution.getSessionId();
        String systemText = promptService.getPrompt(null) + "\n\n" + buildSynthesizePrompt(prevResults);

        log.info("[{}] SYNTHESIZE node executing, prevResults={}", sessionId, prevResults.size());

        return streamByModel(sessionId, systemText, prompt)
                .reduce("", (a, b) -> a + b)
                .flatMapMany(fullResponse -> {
                    NodeResult result = NodeResult.pending(
                            UUID.randomUUID().toString(),
                            execution.getId(),
                            node.getId(),
                            "汇总全部执行结果");
                    result.markRunning();
                    result.markCompleted(fullResponse);
                    log.info("[{}] SYNTHESIZE complete, outputLen={}", sessionId, fullResponse.length());
                    return Flux.just(result);
                });
    }

    private String buildSynthesizePrompt(List<NodeResult> prevResults) {
        StringBuilder sb = new StringBuilder();
        sb.append("[结果汇总模式]\n");
        sb.append("请基于以下所有执行结果，生成一个完整、连贯的回答给用户。\n\n");

        if (prevResults != null && !prevResults.isEmpty()) {
            for (NodeResult r : prevResults) {
                if (r.getOutputText() != null && !r.getOutputText().isBlank()) {
                    sb.append("---\n").append(r.getOutputText()).append("\n");
                }
            }
        } else {
            sb.append("（无执行结果）\n");
        }

        sb.append("\n请以自然、友好的语气直接回答用户。");
        return sb.toString();
    }

    private Flux<String> streamByModel(String sessionId, String systemText, String prompt) {
        return primaryClient.prompt()
                .system(s -> s.text(systemText))
                .user(prompt)
                .options(OpenAiChatOptions.builder().model("qwen-plus").temperature(0.7).build())
                .advisors(a -> a.param("conversation_id", sessionId).param("retrieve_size", 10))
                .stream()
                .content()
                .onErrorResume(e -> {
                    log.warn("[{}] SYNTHESIZE primary failed: {}, falling back", sessionId, e.getMessage());
                    return fallbackClient.prompt()
                            .system(s -> s.text(systemText))
                            .user(prompt)
                            .options(OpenAiChatOptions.builder().model("deepseek-v4-flash").temperature(0.7).build())
                            .stream().content();
                });
    }
}
