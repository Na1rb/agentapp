package com.nairb.ai130.api;

import com.nairb.ai130.app.ChatAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatAppService chatService;

    public ChatController(ChatAppService chatService) { this.chatService = chatService; }

    @GetMapping("/dchat")
    public Flux<String> dchat(@RequestParam("prompt") String prompt,
                              @RequestParam(value = "chatId", required = false) String chatId) {
        String sessionId = (chatId != null && !chatId.isEmpty()) ? chatId : UUID.randomUUID().toString();
        return chatService.streamChat(prompt, sessionId);
    }
}
