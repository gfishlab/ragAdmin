package com.ragadmin.server.infra.ai.chat;

/**
 * 模型返回的问答规划结构化结果。
 * 当前只保留最小可落地字段，优先服务“是否检索、是否联网、检索词重写”三个决策点。
 */
public record ChatExecutionPlanStructuredOutput(
        String intent,
        Boolean needRetrieval,
        String retrievalQuery,
        Boolean needWebSearch,
        String webSearchQuery,
        String reason
) {
}
