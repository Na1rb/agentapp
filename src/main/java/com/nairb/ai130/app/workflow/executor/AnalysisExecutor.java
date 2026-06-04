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

@Component
public class AnalysisExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(AnalysisExecutor.class);

    private final ChatClient primaryClient;
    private final PromptTemplateService promptService;

    public AnalysisExecutor(@Qualifier("chatClient") ChatClient primaryClient,
                            PromptTemplateService promptService) {
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
        String systemText = promptService.getPrompt(null) + "\n\n" + buildAnalysisPrompt();

        log.info("[{}] ANALYSIS node executing", sessionId);

        return primaryClient.prompt()
                .system(s -> s.text(systemText))
                .user(prompt)
                .options(OpenAiChatOptions.builder()
                        .model("qwen-plus")
                        .temperature(0.2)
                        .build())
                .stream()
                .content()
                .reduce("", (a, b) -> a + b)
                .map(fullResponse -> {
                    NodeResult result = NodeResult.pending(
                            UUID.randomUUID().toString(),
                            execution.getId(),
                            node.getId(),
                            prompt);
                    result.markRunning();
                    result.markCompleted(fullResponse);
                    log.info("[{}] ANALYSIS complete, outputLen={}", sessionId, fullResponse.length());
                    return result;
                })
                .flux();
    }

    private String buildAnalysisPrompt() {
        return """
                [Task analysis mode]
                Decide whether the user's request needs a multi-step workflow.

                Set requiresWorkflow=true when the request needs two or more dependent actions,
                multiple tools or files, parallel subtasks, verification, or an explicit deliverable chain.
                Set requiresWorkflow=false for simple questions, explanations, rewrites, translations,
                and other single-action requests.

                Output JSON only:
                {
                  "requiresWorkflow": true,
                  "complexity": "simple|medium|complex",
                  "goal": "one sentence goal",
                  "requirements": ["requirement 1", "requirement 2"],
                  "reason": "why a workflow is or is not needed"
                }
                """;
    }
}
