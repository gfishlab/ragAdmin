package com.ragadmin.server.chat.dto;

/**
 * 面向前端的回答后处理元数据。
 */
public record ChatAnswerMetadataResponse(
        String confidence,
        boolean hasKnowledgeBaseEvidence,
        boolean needFollowUp
) {
}
