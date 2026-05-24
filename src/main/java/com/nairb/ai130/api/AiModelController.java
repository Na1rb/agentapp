package com.nairb.ai130.api;

import com.nairb.ai130.common.response.ApiResponse;
import com.nairb.ai130.domain.model.AiModelConfig;
import com.nairb.ai130.infrastructure.repository.AiModelRepository;
import com.nairb.ai130.types.vo.AiModelVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 模型查询接口 —— 供前端下拉选择模型。
 */
@RestController
@RequestMapping("/api/ai-models")
@CrossOrigin(origins = "*")
public class AiModelController {

    private final AiModelRepository repo;

    public AiModelController(AiModelRepository repo) {
        this.repo = repo;
    }

    /** 获取所有启用的模型列表 */
    @GetMapping
    public ApiResponse<List<AiModelVO>> list() {
        List<AiModelConfig> configs = repo.findEnabled();
        List<AiModelVO> vos = configs.stream()
                .map(c -> new AiModelVO(
                        c.getModelCode(),
                        c.getProvider(),
                        c.getModelName(),
                        c.getDescription(),
                        c.getIsDefault()))
                .toList();
        return ApiResponse.success(vos);
    }

    /** 获取默认模型 */
    @GetMapping("/default")
    public ApiResponse<AiModelVO> defaultModel() {
        AiModelConfig c = repo.findDefault();
        if (c == null) {
            return ApiResponse.notFound("无默认模型配置");
        }
        return ApiResponse.success(new AiModelVO(
                c.getModelCode(), c.getProvider(), c.getModelName(),
                c.getDescription(), c.getIsDefault()));
    }
}
