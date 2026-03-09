package com.ragadmin.server.document.support;

public record EmbeddingModelDescriptor(
        Long modelId,
        String modelCode,
        String providerCode,
        String providerName
) {
}
