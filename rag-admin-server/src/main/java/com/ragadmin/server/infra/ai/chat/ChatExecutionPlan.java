package com.ragadmin.server.infra.ai.chat;

/**
 * 问答前置规划结果。
 */
public record ChatExecutionPlan(
        String intent,
        boolean needRetrieval,
        String retrievalQuery,
        boolean needWebSearch,
        String webSearchQuery,
        String reason,
        String source
) {
}
