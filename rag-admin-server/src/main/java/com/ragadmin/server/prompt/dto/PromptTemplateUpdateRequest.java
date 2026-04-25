package com.ragadmin.server.prompt.dto;

import jakarta.validation.constraints.NotBlank;

public record PromptTemplateUpdateRequest(
        @NotBlank String templateName,
        @NotBlank String promptContent,
        String description,
        String status
) {
}
