package com.ragadmin.server.document.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.ragadmin.server.common.model.ApiResponse;
import com.ragadmin.server.document.dto.DocumentUploadCapabilityResponse;
import com.ragadmin.server.document.dto.UploadUrlRequest;
import com.ragadmin.server.document.dto.UploadUrlResponse;
import com.ragadmin.server.document.service.FileUploadService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/files")
@SaCheckLogin(type = "admin")
@SaCheckPermission("KB_MANAGE")
public class FileController {

    @Autowired
    private FileUploadService fileUploadService;

    @GetMapping("/upload-capability")
    public ApiResponse<DocumentUploadCapabilityResponse> getUploadCapability() {
        return ApiResponse.success(fileUploadService.getUploadCapability());
    }

    @PostMapping("/upload-url")
    public ApiResponse<UploadUrlResponse> createUploadUrl(@Valid @RequestBody UploadUrlRequest request) {
        return ApiResponse.success(fileUploadService.createUploadUrl(request));
    }
}
