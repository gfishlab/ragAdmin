package com.ragadmin.server.model.dto;

public record ModelProviderResponse(
        Long id,
        String providerCode,
        String providerName,
        String baseUrl,
        String apiKeySecretRef,
        String status
) {
}
