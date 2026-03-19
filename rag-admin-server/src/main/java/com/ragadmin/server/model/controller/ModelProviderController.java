package com.ragadmin.server.model.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.ragadmin.server.common.model.ApiResponse;
import com.ragadmin.server.model.dto.CreateModelProviderRequest;
import com.ragadmin.server.model.dto.ModelProviderHealthCheckResponse;
import com.ragadmin.server.model.dto.ModelProviderResponse;
import com.ragadmin.server.model.service.ModelProviderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/model-providers")
@SaCheckLogin(type = "admin")
@SaCheckPermission("MODEL_MANAGE")
public class ModelProviderController {

    @Autowired
    private ModelProviderService modelProviderService;

    @GetMapping
    public ApiResponse<List<ModelProviderResponse>> list() {
        return ApiResponse.success(modelProviderService.list());
    }

    @PostMapping
    public ApiResponse<ModelProviderResponse> create(@Valid @RequestBody CreateModelProviderRequest request) {
        return ApiResponse.success(modelProviderService.create(request));
    }

    @PostMapping("/{providerId}/health-check")
    public ApiResponse<ModelProviderHealthCheckResponse> healthCheck(@PathVariable Long providerId) {
        return ApiResponse.success(modelProviderService.healthCheck(providerId));
    }
}
