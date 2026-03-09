package com.ragadmin.server.system.controller;

import com.ragadmin.server.common.model.ApiResponse;
import com.ragadmin.server.system.dto.HealthCheckResponse;
import com.ragadmin.server.system.service.SystemHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system")
public class SystemHealthController {

    private final SystemHealthService systemHealthService;

    public SystemHealthController(SystemHealthService systemHealthService) {
        this.systemHealthService = systemHealthService;
    }

    @GetMapping("/health")
    public ApiResponse<HealthCheckResponse> health() {
        return ApiResponse.success(systemHealthService.check());
    }
}
