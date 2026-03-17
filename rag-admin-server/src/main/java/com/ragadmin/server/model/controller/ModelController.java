package com.ragadmin.server.model.controller;

import com.ragadmin.server.common.model.ApiResponse;
import com.ragadmin.server.common.model.PageResponse;
import com.ragadmin.server.model.dto.ModelHealthCheckResponse;
import com.ragadmin.server.model.dto.ModelResponse;
import com.ragadmin.server.model.dto.CreateModelRequest;
import com.ragadmin.server.model.dto.UpdateModelRequest;
import com.ragadmin.server.model.service.ModelService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/models")
public class ModelController {

    private static final Logger log = LoggerFactory.getLogger(ModelController.class);

    @Autowired
    private ModelService modelService;

    @GetMapping
    public ApiResponse<PageResponse<ModelResponse>> list(
            @RequestParam(required = false) String providerCode,
            @RequestParam(required = false) String capabilityType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize
    ) {
        return ApiResponse.success(modelService.list(providerCode, capabilityType, status, pageNo, pageSize));
    }

    @PostMapping
    public ApiResponse<ModelResponse> create(@Valid @RequestBody CreateModelRequest request) {
        return ApiResponse.success(modelService.create(request));
    }

    @PutMapping("/{modelId}")
    public ApiResponse<ModelResponse> update(@PathVariable Long modelId, @Valid @RequestBody UpdateModelRequest request) {
        return ApiResponse.success(modelService.update(modelId, request));
    }

    @DeleteMapping("/{modelId}")
    public ApiResponse<Void> delete(@PathVariable Long modelId) {
        modelService.delete(modelId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{modelId}/health-check")
    public ApiResponse<ModelHealthCheckResponse> healthCheck(@PathVariable Long modelId) {
        ModelResponse model = modelService.get(modelId);
        log.info("开始模型探活，modelId={}, modelName={}, modelCode={}",
                modelId, model.modelName(), model.modelCode());
        try {
            ModelHealthCheckResponse response = modelService.healthCheck(modelId);
            log.info("模型探活完成，modelId={}, modelName={}, modelCode={}, status={}",
                    modelId, model.modelName(), model.modelCode(), response.status());
            return ApiResponse.success(response);
        } catch (RuntimeException ex) {
            log.warn("模型探活失败，modelId={}, modelName={}, modelCode={}, message={}",
                    modelId, model.modelName(), model.modelCode(), ex.getMessage());
            throw ex;
        }
    }
}
