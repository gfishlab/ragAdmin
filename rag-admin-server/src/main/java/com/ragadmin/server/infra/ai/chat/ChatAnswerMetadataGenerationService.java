package com.ragadmin.server.infra.ai.chat;

public interface ChatAnswerMetadataGenerationService {

    ChatAnswerMetadata generate(ChatAnswerMetadataGenerationRequest request);
}
