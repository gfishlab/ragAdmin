package com.ragadmin.server.chat.dto;

import java.util.List;

public record ChatResponse(
        Long messageId,
        String answer,
        List<ChatReferenceResponse> references,
        ChatUsageResponse usage,
        ChatAnswerMetadataResponse metadata
) {
}
