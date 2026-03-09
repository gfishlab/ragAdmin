package com.ragadmin.server.model.dto;

public record ModelCapabilityHealthResponse(
        String capabilityType,
        String status,
        String message
) {
}
