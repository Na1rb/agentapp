package com.nairb.ai130.api;

import com.nairb.ai130.app.SessionAppService;
import com.nairb.ai130.common.exception.BusinessException;
import com.nairb.ai130.common.response.ApiResponse;
import com.nairb.ai130.types.dto.MessageVO;
import com.nairb.ai130.types.dto.SessionInfoVO;
import com.nairb.ai130.types.dto.SessionVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class SessionController {
    private static final Logger log = LoggerFactory.getLogger(SessionController.class);

    private final SessionAppService sessionService;

    public SessionController(SessionAppService sessionService) { this.sessionService = sessionService; }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<SessionVO>>> list(@RequestParam long userId) {
        try { return ResponseEntity.ok(ApiResponse.success(sessionService.list(userId))); }
        catch (Exception e) { return ResponseEntity.ok(ApiResponse.success(List.of())); }
    }

    @GetMapping("/session/{chatId}/info")
    public ResponseEntity<ApiResponse<SessionInfoVO>> info(@PathVariable String chatId) {
        return ResponseEntity.ok(ApiResponse.success(sessionService.info(chatId)));
    }

    @GetMapping("/session/{chatId}/messages")
    public ResponseEntity<ApiResponse<List<MessageVO>>> messages(@PathVariable String chatId,
                                                                  @RequestParam(defaultValue = "40") int limit) {
        int safe = Math.min(Math.max(limit, 1), 40);
        try { return ResponseEntity.ok(ApiResponse.success(sessionService.messages(chatId, safe))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(ApiResponse.badRequest("Failed")); }
    }

    /**
     * 重命名会话。
     * <pre>PUT /api/session/{chatId}/rename</pre>
     * 请求体: {"title": "新名称"}
     */
    @PutMapping("/session/{chatId}/rename")
    public ResponseEntity<ApiResponse<String>> rename(@PathVariable String chatId,
                                                       @RequestBody Map<String, String> body) {
        String title = body.get("title");
        if (title == null || title.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.badRequest("title is required"));
        }
        try {
            sessionService.rename(chatId, title.trim());
            return ResponseEntity.ok(ApiResponse.success("renamed"));
        } catch (BusinessException e) {
            return ResponseEntity.status(e.getCode() >= 500 ? 500 : 400)
                    .body(ApiResponse.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.serverError(e.getMessage()));
        }
    }

    @DeleteMapping("/session/{chatId}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable String chatId) {
        try { sessionService.delete(chatId); return ResponseEntity.ok(ApiResponse.success("deleted")); }
        catch (BusinessException e) { return ResponseEntity.status(e.getCode() >= 500 ? 500 : 400).body(ApiResponse.error(e.getCode(), e.getMessage())); }
        catch (Exception e) { return ResponseEntity.internalServerError().body(ApiResponse.serverError(e.getMessage())); }
    }
}
