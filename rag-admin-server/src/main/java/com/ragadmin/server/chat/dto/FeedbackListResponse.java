package com.ragadmin.server.chat.dto;

import java.time.LocalDateTime;

public record FeedbackListResponse(
        Long id,
        Long messageId,
        Long userId,
        String username,
        String feedbackType,
        String commentText,
        String questionSummary,
        String answerSummary,
        Long sessionId,
        LocalDateTime createdAt
) {
}
