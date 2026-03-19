package com.ragadmin.server.system.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.ragadmin.server.common.model.ApiResponse;
import com.ragadmin.server.system.dto.HealthCheckResponse;
import com.ragadmin.server.system.service.SystemHealthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system")
@SaCheckLogin(type = "admin")
public class SystemHealthController {

    @Autowired
    private SystemHealthService systemHealthService;

    @GetMapping("/health")
    @SaCheckPermission("DASHBOARD_VIEW")
    public ApiResponse<HealthCheckResponse> health() {
        return ApiResponse.success(systemHealthService.check());
    }
}
