package com.nairb.ai130.api;

import com.nairb.ai130.app.ChatAppService;
import com.nairb.ai130.domain.model.AiModelConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;

    public ChatController(ChatAppService chatService, JdbcTemplate jdbcTemplate) { 
        this.chatService = chatService; 
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/dchat")
    public Flux<String> dchat(@RequestParam("prompt") String prompt,
                              @RequestParam(value = "chatId", required = false) String chatId,
                              @RequestParam(value = "agentId", required = false, defaultValue = "default") String agentId,
                              @RequestParam(value = "primaryModelId", required = false, defaultValue = "qwen-plus-01") String primaryModelId,
                              @RequestParam(value = "fallbackModelId", required = false, defaultValue = "deepseek-chat-01") String fallbackModelId) {
        String sessionId = (chatId != null && !chatId.isEmpty()) ? chatId : UUID.randomUUID().toString();
        return chatService.streamChat(prompt, sessionId, agentId, primaryModelId, fallbackModelId);
    }

    @GetMapping("/models")
    public List<AiModelConfig> getActiveModels() {
        String sql = "SELECT id, model_name, provider, base_url, api_key, is_active, description FROM sys_ai_model WHERE is_active = true";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            AiModelConfig config = new AiModelConfig();
            config.setId(rs.getString("id"));
            config.setModelName(rs.getString("model_name"));
            config.setProvider(rs.getString("provider"));
            config.setBaseUrl(rs.getString("base_url"));
            // 为了安全起见，不要把 api_key 返回给前端
            config.setApiKey(null);
            config.setActive(rs.getBoolean("is_active"));
            config.setDescription(rs.getString("description"));
            return config;
        });
    }
}
