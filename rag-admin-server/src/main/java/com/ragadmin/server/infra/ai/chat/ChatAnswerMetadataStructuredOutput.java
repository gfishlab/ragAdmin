package com.ragadmin.server.infra.ai.chat;

/**
 * 模型返回的回答后处理元数据结构化结果。
 */
public record ChatAnswerMetadataStructuredOutput(
        String confidence,
        Boolean hasKnowledgeBaseEvidence,
        Boolean needFollowUp,
        String reason
) {
}
