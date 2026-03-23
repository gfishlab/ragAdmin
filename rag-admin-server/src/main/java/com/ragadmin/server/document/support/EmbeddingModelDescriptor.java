package com.ragadmin.server.document.support;

import com.ragadmin.server.infra.ai.embedding.EmbeddingExecutionMode;

public record EmbeddingModelDescriptor(
        Long modelId,
        String modelCode,
        String providerCode,
        String providerName,
        EmbeddingExecutionMode executionMode
) {
}
