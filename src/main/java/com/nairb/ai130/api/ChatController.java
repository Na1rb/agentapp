package com.nairb.ai130.api;

import com.nairb.ai130.api.dto.ChatRequest;
import com.nairb.ai130.app.ChatAppService;
import com.nairb.ai130.app.SessionAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatAppService chatService;
    private final SessionAppService sessionService;

    public ChatController(ChatAppService chatService, SessionAppService sessionService) {
        this.chatService = chatService;
        this.sessionService = sessionService;
    }

    // ==================== GET: 简化对话（向后兼容） ====================

    @GetMapping(value = "/dchat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> dchat(@RequestParam("prompt") String prompt,
                              @RequestParam(value = "chatId", required = false) String chatId,
                              @RequestParam(value = "userId", required = false) Long userId,
                              @RequestParam(value = "toolIds", required = false) List<String> toolIds,
                              @RequestParam(value = "modelCode", required = false) String modelCode,
                              @RequestParam(value = "promptCode", required = false) String promptCode,
                              @RequestParam(value = "strategy", required = false) String strategy) {
        String sessionId = (chatId != null && !chatId.isEmpty()) ? chatId : UUID.randomUUID().toString();
        String selectedModel = (modelCode != null && !modelCode.isBlank()) ? modelCode : null;

        log.info("GET /api/dchat session={}, userId={}, prompt={}, tools={}, model={}, promptCode={}, strategy={}",
                sessionId, userId, prompt, toolIds, selectedModel, promptCode, strategy);

        // 自动创建用户会话关联
        ensureUserSession(userId, sessionId, prompt);

        // 分步编排模式
        if ("STEP_CHECK".equalsIgnoreCase(strategy)) {
            return chatService.streamChatWithSteps(prompt, sessionId,
                    toolIds != null ? toolIds : List.of(), selectedModel, promptCode);
        }

        // 默认流式模式：将纯文本包装为 ServerSentEvent（无 event 字段，即 data: 行）
        List<String> safeToolIds = toolIds != null ? toolIds : List.of();
        return chatService.streamChat(prompt, sessionId, safeToolIds, selectedModel, promptCode)
                .map(s -> ServerSentEvent.<String>builder().data(s).build());
    }

    // ==================== POST: 完整对话（支持工具选择） ====================

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@RequestBody ChatRequest request) {
        String sessionId = (request.getChatId() != null && !request.getChatId().isEmpty())
                ? request.getChatId()
                : UUID.randomUUID().toString();
        List<String> toolIds = request.getToolIds();
        String strategy = request.getStrategy();
        String modelCode = request.getModelCode();
        String promptCode = request.getPromptCode();
        String selectedModel = (modelCode != null && !modelCode.isBlank()) ? modelCode : null;

        log.info("POST /api/chat session={}, userId={}, prompt={}, tools={}, model={}, promptCode={}, strategy={}",
                sessionId, request.getUserId(), request.getPrompt(), toolIds, selectedModel, promptCode, strategy);

        // 自动创建用户会话关联
        ensureUserSession(request.getUserId(), sessionId, request.getPrompt());

        // 分步编排模式
        if ("STEP_CHECK".equalsIgnoreCase(strategy)) {
            return chatService.streamChatWithSteps(request.getPrompt(), sessionId, toolIds, selectedModel, promptCode);
        }

        // 默认流式模式：将纯文本包装为 ServerSentEvent
        return chatService.streamChat(request.getPrompt(), sessionId, toolIds, selectedModel, promptCode)
                .map(s -> ServerSentEvent.<String>builder().data(s).build());
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 确保用户会话关联存在（不存在则自动创建）。
     * title 取 prompt 前 30 个字符，过长用 … 截断。
     */
    private void ensureUserSession(Long userId, String chatId, String prompt) {
        if (userId == null || userId <= 0) return;
        String title = prompt;
        if (prompt != null && prompt.length() > 30) {
            title = prompt.substring(0, 30).replace('\n', ' ') + "…";
        }
        try {
            sessionService.createUserSession(userId, chatId, title);
        } catch (Exception e) {
            log.warn("Failed to create user session for chatId={}: {}", chatId, e.getMessage());
        }
    }
}
