package com.nairb.ai130.app.workflow.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nairb.ai130.app.PromptTemplateService;
import com.nairb.ai130.domain.workflow.CheckResult;
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
 * CHECK 节点执行器 — 使用独立模型/评估标准对执行结果进行结构化校验。
 * <p>
 * 与执行阶段使用不同的模型（或不同的 system prompt），
 * 输出结构化的 passed/failed 判定，供条件边路由。
 * <p>
 * 分离执行者与检查者，避免同一个模型既做执行又做评估的倾向性偏差。
 */
@Component
public class CheckExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(CheckExecutor.class);

    private static final ObjectMapper mapper = new ObjectMapper();

    /** 检查阶段使用降级模型（DeepSeek），与执行模型分离 */
    private final ChatClient checkerClient;
    private final PromptTemplateService promptService;

    public CheckExecutor(@Qualifier("deepseekChatClient") ChatClient checkerClient,
                         PromptTemplateService promptService) {
        this.checkerClient = checkerClient;
        this.promptService = promptService;
    }

    @Override
    public Flux<NodeResult> execute(WorkflowExecution execution,
                                    WorkflowDefinition.NodeDef node,
                                    WorkflowDefinition definition,
                                    List<NodeResult> prevResults,
                                    String prompt) {
        String sessionId = execution.getSessionId();

        String checkPrompt = buildCheckPrompt(prompt, prevResults);
        String systemText = promptService.getPrompt(null) + "\n\n" + buildCheckerSystemPrompt();

        log.info("[{}] CHECK node executing, prevResults={}", sessionId, prevResults.size());

        return checkerClient.prompt()
                .system(s -> s.text(systemText))
                .user(checkPrompt)
                .options(OpenAiChatOptions.builder()
                        .model("deepseek-v4-flash")
                        .temperature(0.3)  // 低温度，提高判定稳定性
                        .build())
                .stream()
                .content()
                .reduce("", (a, b) -> a + b)
                .flatMapMany(fullResponse -> {
                    CheckResult checkResult = parseCheckResult(fullResponse);

                    NodeResult nodeResult = NodeResult.pending(
                            UUID.randomUUID().toString(),
                            execution.getId(),
                            node.getId(),
                            checkPrompt);
                    nodeResult.markRunning();

                    if (checkResult.isPassed()) {
                        nodeResult.markCompleted("检查通过：" + String.join("; ", checkResult.getReasons()));
                    } else {
                        nodeResult.markCompleted("检查不通过：" + String.join("; ", checkResult.getReasons())
                                + " | 建议：" + (checkResult.getSuggestion() != null ? checkResult.getSuggestion() : ""));
                    }

                    // 将结构化检查结果序列化为 JSON 存入 check_result 字段
                    try {
                        nodeResult.setCheckResultJson(mapper.writeValueAsString(checkResult));
                    } catch (JsonProcessingException e) {
                        log.warn("[{}] Failed to serialize CheckResult", sessionId);
                    }

                    log.info("[{}] CHECK result: passed={}, score={}, reasons={}",
                            sessionId, checkResult.isPassed(), checkResult.getScore(),
                            checkResult.getReasons());

                    return Flux.just(nodeResult);
                });
    }

    /**
     * 构建检查器的 System Prompt — 与执行器的风格不同，强调客观评估。
     */
    private String buildCheckerSystemPrompt() {
        return """
                [质量检查模式]
                你是一个严格但公正的质量检查员。你的职责是对 AI 助手的执行结果进行客观评估。

                评估标准：
                1. 准确性：结果是否正确、无事实错误
                2. 完整性：是否涵盖了用户问题的所有方面
                3. 清晰度：表达是否清晰、结构化
                4. 实用性：结果是否对用户有帮助

                请输出严格的 JSON 格式：
                ```json
                {
                  "passed": true/false,
                  "score": 0.0-1.0,
                  "reasons": ["原因1", "原因2"],
                  "suggestion": "改进建议"
                }
                ```

                如果结果质量良好，passed 为 true。
                如果存在明显问题，passed 为 false 并说明原因。
                只输出 JSON，不要包含其他文字。
                """;
    }

    private String buildCheckPrompt(String originalPrompt, List<NodeResult> prevResults) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 用户原始请求\n").append(originalPrompt).append("\n\n");
        sb.append("## 执行结果\n");

        if (prevResults != null) {
            for (NodeResult r : prevResults) {
                if (r.getOutputText() != null && !r.getOutputText().isBlank()) {
                    sb.append("### 节点 ").append(r.getNodeId()).append("\n");
                    sb.append(r.getOutputText()).append("\n\n");
                }
            }
        }

        sb.append("请按评估标准对以上结果进行严格检查，输出 JSON 判定。");
        return sb.toString();
    }

    /**
     * 解析模型输出中的 CheckResult JSON。
     * 解析失败时返回一个安全的默认失败结果。
     */
    private CheckResult parseCheckResult(String text) {
        if (text == null || text.isBlank()) {
            return new CheckResult(false, 0.0, List.of("检查器无输出"), "请重新执行");
        }

        try {
            // 尝试提取 JSON（支持 ```json 包裹）
            String json = text;
            int start = text.indexOf("```json");
            if (start >= 0) {
                start += 7;
                int end = text.indexOf("```", start);
                if (end > start) json = text.substring(start, end).trim();
            }
            int braceStart = json.indexOf("{");
            int braceEnd = json.lastIndexOf("}");
            if (braceStart >= 0 && braceEnd > braceStart) {
                json = json.substring(braceStart, braceEnd + 1);
            }

            CheckResult cr = mapper.readValue(json, CheckResult.class);
            return cr != null ? cr : new CheckResult(false, 0.0, List.of("无法解析检查结果"), "");
        } catch (Exception e) {
            log.warn("Failed to parse CheckResult JSON, defaulting to fail: {}", e.getMessage());
            return new CheckResult(false, 0.0, List.of("检查结果格式异常", e.getMessage()), "请重试");
        }
    }
}
