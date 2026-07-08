package com.intern.aimodel;

import com.intern.common.ApiResponse;
import com.intern.model.entity.AiModel;
import com.intern.model.entity.ModelCallLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AiModelController {
    private final AiModelService aiModelService;

    public AiModelController(AiModelService aiModelService) {
        this.aiModelService = aiModelService;
    }

    @GetMapping("/api/models")
    public ApiResponse<List<AiModel>> listModels() {
        return ApiResponse.ok(aiModelService.listModels());
    }

    @PutMapping("/api/models/{id}")
    public ApiResponse<AiModel> update(@PathVariable Long id, @RequestBody AiModel model) {
        return ApiResponse.ok(aiModelService.update(id, model));
    }

    @GetMapping("/api/logs")
    public ApiResponse<List<ModelCallLog>> listLogs() {
        return ApiResponse.ok(aiModelService.listLogs());
    }
}
