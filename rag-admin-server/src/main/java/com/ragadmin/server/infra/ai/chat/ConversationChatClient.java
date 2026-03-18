package com.ragadmin.server.infra.ai.chat;

import java.util.List;

public interface ConversationChatClient {

    ChatModelClient.ChatCompletionResult chat(
            String providerCode,
            String modelCode,
            String conversationId,
            List<ChatModelClient.ChatMessage> promptMessages,
            List<ChatModelClient.ChatMessage> historyMessages
    );
}
