package com.nairb.ai130.api;

import com.nairb.ai130.app.ModelAppService;
import com.nairb.ai130.common.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 模型列表 API。
 * <p>
 * 前端通过此接口获取可选模型列表，用于模型选择器。
 *
 * <pre>
 * GET /api/ai-models       → 可用模型列表
 * GET /api/ai-models/default → 默认模型
 * </pre>
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ModelController {

    private static final Logger log = LoggerFactory.getLogger(ModelController.class);

    private final ModelAppService modelService;

    public ModelController(ModelAppService modelService) {
        this.modelService = modelService;
    }

    /**
     * 获取所有可用模型列表。
     */
    @GetMapping("/ai-models")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> listModels() {
        try {
            return ResponseEntity.ok(ApiResponse.success(modelService.listModels()));
        } catch (Exception e) {
            log.error("Failed to list models", e);
            return ResponseEntity.internalServerError().body(ApiResponse.serverError(e.getMessage()));
        }
    }

    /**
     * 获取默认模型。
     */
    @GetMapping("/ai-models/default")
    public ResponseEntity<ApiResponse<Map<String, String>>> getDefaultModel() {
        try {
            return ResponseEntity.ok(ApiResponse.success(modelService.getDefaultModel()));
        } catch (Exception e) {
            log.error("Failed to get default model", e);
            return ResponseEntity.internalServerError().body(ApiResponse.serverError(e.getMessage()));
        }
    }
}
