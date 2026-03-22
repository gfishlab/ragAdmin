package com.ragadmin.server.infra.ai.chat;

/**
 * 回答后处理元数据生成请求。
 */
public record ChatAnswerMetadataGenerationRequest(
        String providerCode,
        String modelCode,
        String question,
        String answer,
        int referenceCount
) {
}
