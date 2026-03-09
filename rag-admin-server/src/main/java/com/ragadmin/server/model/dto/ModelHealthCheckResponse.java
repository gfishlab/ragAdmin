package com.ragadmin.server.model.dto;

import java.util.List;

public record ModelHealthCheckResponse(
        Long modelId,
        String modelCode,
        String providerCode,
        String status,
        String message,
        List<ModelCapabilityHealthResponse> capabilityChecks
) {
}
