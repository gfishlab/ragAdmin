package com.ragadmin.server.infra.ai.chat;

public interface ChatExecutionPlanningService {

    ChatExecutionPlan plan(ChatExecutionPlanningRequest request);
}
