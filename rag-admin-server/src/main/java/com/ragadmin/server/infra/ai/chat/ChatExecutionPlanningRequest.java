package com.ragadmin.server.infra.ai.chat;

/**
 * 问答前置规划请求。
 * 该对象只描述当前问题是否具备检索、联网等可选能力，不直接承载具体执行结果。
 */
public record ChatExecutionPlanningRequest(
        String providerCode,
        String modelCode,
        String question,
        boolean retrievalAvailable,
        boolean webSearchAvailable,
        int selectedKnowledgeBaseCount,
        boolean knowledgeBaseScene
) {
}
