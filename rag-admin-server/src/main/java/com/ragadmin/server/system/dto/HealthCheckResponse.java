package com.ragadmin.server.system.dto;

public record HealthCheckResponse(
        String status,
        DependencyHealthResponse postgres,
        DependencyHealthResponse redis,
        DependencyHealthResponse minio
) {
}
