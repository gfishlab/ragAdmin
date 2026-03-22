package com.ragadmin.server.infra.ai.chat;

/**
 * 回答后处理元数据。
 * 该对象用于服务内部编排，再由聊天响应 DTO 显式映射到外部接口。
 */
public record ChatAnswerMetadata(
        String confidence,
        boolean hasKnowledgeBaseEvidence,
        boolean needFollowUp
) {
}
