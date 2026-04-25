package com.ragadmin.server.prompt.dto;

import java.time.LocalDateTime;

public record PromptTemplateResponse(
        Long id,
        String templateCode,
        String templateName,
        String capabilityType,
        String promptContent,
        Integer versionNo,
        String status,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
