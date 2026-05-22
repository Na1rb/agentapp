package com.nairb.ai130.api;

import com.nairb.ai130.common.response.ApiResponse;
import com.nairb.ai130.domain.model.AiModelConfig;
import com.nairb.ai130.infrastructure.repository.AiModelRepository;
import com.nairb.ai130.types.AiModelVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai-models")
@CrossOrigin(origins = "*")
public class AiModelController {

    private final AiModelRepository aiModelRepository;

    public AiModelController(AiModelRepository aiModelRepository) {
        this.aiModelRepository = aiModelRepository;
    }

    @GetMapping
    public ApiResponse<List<AiModelVO>> listEnabledModels() {
        List<AiModelConfig> configs = aiModelRepository.findAllEnabledModels();
        List<AiModelVO> vos = configs.stream().map(config -> new AiModelVO(
                config.getModelCode(),
                config.getProvider(),
                config.getModelName(),
                config.getDescription(),
                config.getIsDefault()
        )).collect(Collectors.toList());
        return ApiResponse.success(vos);
    }

    @GetMapping("/default")
    public ApiResponse<AiModelVO> getDefaultModel() {
        AiModelConfig config = aiModelRepository.findDefaultModel();
        if (config == null) {
            return ApiResponse.error(404, "No default model found");
        }
        return ApiResponse.success(new AiModelVO(
                config.getModelCode(),
                config.getProvider(),
                config.getModelName(),
                config.getDescription(),
                config.getIsDefault()
        ));
    }
}
