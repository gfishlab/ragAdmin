package com.ragadmin.server.prompt.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.ragadmin.server.common.model.ApiResponse;
import com.ragadmin.server.common.model.PageResponse;
import com.ragadmin.server.prompt.dto.PromptTemplateResponse;
import com.ragadmin.server.prompt.dto.PromptTemplateUpdateRequest;
import com.ragadmin.server.prompt.service.PromptTemplateAdminService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/prompt-templates")
@SaCheckLogin(type = "admin")
@SaCheckPermission("PROMPT_TEMPLATE_MANAGE")
public class PromptTemplateAdminController {

    @Autowired
    private PromptTemplateAdminService promptTemplateAdminService;

    @GetMapping
    public ApiResponse<PageResponse<PromptTemplateResponse>> list(
            @RequestParam(required = false) String templateCode,
            @RequestParam(required = false) String capabilityType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize
    ) {
        return ApiResponse.success(promptTemplateAdminService.listTemplates(
                templateCode, capabilityType, status, pageNo, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<PromptTemplateResponse> get(@PathVariable Long id) {
        return ApiResponse.success(promptTemplateAdminService.getTemplate(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<PromptTemplateResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid PromptTemplateUpdateRequest request
    ) {
        return ApiResponse.success(promptTemplateAdminService.updateTemplate(id, request));
    }
}
