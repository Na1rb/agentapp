package com.nairb.ai130.api;

import com.nairb.ai130.app.PromptTemplateService;
import com.nairb.ai130.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 预设角色模板 API。
 * <pre>GET /api/prompts → 返回角色列表 [{code, name}]</pre>
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class PromptController {

    private final PromptTemplateService promptService;

    public PromptController(PromptTemplateService promptService) {
        this.promptService = promptService;
    }

    @GetMapping("/prompts")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> list() {
        return ResponseEntity.ok(ApiResponse.success(promptService.listRoles()));
    }
}
