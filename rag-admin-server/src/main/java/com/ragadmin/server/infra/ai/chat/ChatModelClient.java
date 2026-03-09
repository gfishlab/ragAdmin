package com.ragadmin.server.infra.ai.chat;

import java.util.List;

public interface ChatModelClient {

    boolean supports(String providerCode);

    ChatCompletionResult chat(String modelCode, List<ChatMessage> messages);

    record ChatMessage(String role, String content) {
    }

    record ChatCompletionResult(String content, Integer promptTokens, Integer completionTokens) {
    }
}
