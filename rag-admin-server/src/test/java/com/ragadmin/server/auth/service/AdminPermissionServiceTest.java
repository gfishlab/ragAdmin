package com.ragadmin.server.auth.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminPermissionServiceTest {

    private final AdminPermissionService adminPermissionService = new AdminPermissionService();

    @Test
    void shouldReturnAllPermissionsForAdmin() {
        List<String> permissions = adminPermissionService.listPermissionsByRoleCodes(List.of("ADMIN"));

        assertTrue(permissions.contains("USER_MANAGE"));
        assertTrue(permissions.contains("MODEL_MANAGE"));
        assertTrue(permissions.contains("TASK_OPERATE"));
    }

    @Test
    void shouldReturnExpectedPermissionsForKnowledgeBaseAdmin() {
        List<String> permissions = adminPermissionService.listPermissionsByRoleCodes(List.of("KB_ADMIN"));

        assertEquals(
                List.of(
                        "DASHBOARD_VIEW",
                        "CHAT_CONSOLE_ACCESS",
                        "KB_MANAGE",
                        "MODEL_MANAGE",
                        "TASK_VIEW",
                        "TASK_OPERATE",
                        "STATISTICS_VIEW"
                ),
                permissions
        );
    }

    @Test
    void shouldReturnExpectedPermissionsForAuditor() {
        List<String> permissions = adminPermissionService.listPermissionsByRoleCodes(List.of("AUDITOR"));

        assertEquals(
                List.of(
                        "DASHBOARD_VIEW",
                        "TASK_VIEW",
                        "AUDIT_VIEW",
                        "STATISTICS_VIEW"
                ),
                permissions
        );
    }

    @Test
    void shouldReturnEmptyPermissionsForAppUser() {
        List<String> permissions = adminPermissionService.listPermissionsByRoleCodes(List.of("APP_USER"));

        assertTrue(permissions.isEmpty());
    }
}
