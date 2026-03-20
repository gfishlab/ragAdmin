package com.ragadmin.server.infra.ai.chat;

import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.infra.ai.SpringAiModelFactory;
import com.ragadmin.server.infra.ai.SpringAiModelSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Component
public class SpringAiConversationChatClient implements ConversationChatClient {

    @Autowired
    private SpringAiModelFactory springAiModelFactory;

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    private ChatClientAdvisorProperties chatClientAdvisorProperties;

    @Override
    public ChatModelClient.ChatCompletionResult chat(
            String providerCode,
            String modelCode,
            String conversationId,
            List<ChatModelClient.ChatMessage> promptMessages,
            List<ChatModelClient.ChatMessage> historyMessages
    ) {
        if (!StringUtils.hasText(conversationId)) {
            throw new BusinessException("CHAT_CONVERSATION_ID_INVALID", "会话记忆 conversationId 不能为空", HttpStatus.BAD_REQUEST);
        }
        if (promptMessages == null || promptMessages.isEmpty()) {
            throw new BusinessException("CHAT_PROMPT_EMPTY", "聊天提示消息不能为空", HttpStatus.BAD_REQUEST);
        }

        // 已有历史业务消息的旧会话首次切到 memory 链路时，需要先补种，避免上下文突然丢失。
        seedConversationMemoryIfNecessary(conversationId, historyMessages);

        var chatModel = springAiModelFactory.createChatModel(providerCode, modelCode);
        ChatClient chatClient = buildChatClient(chatModel);
        var response = chatClient.prompt()
                .messages(SpringAiModelSupport.toSpringMessages(promptMessages))
                .advisors(spec -> spec
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .chatResponse();
        return SpringAiModelSupport.toChatCompletionResult(response);
    }

    @Override
    public Flux<org.springframework.ai.chat.model.ChatResponse> stream(
            String providerCode,
            String modelCode,
            String conversationId,
            List<ChatModelClient.ChatMessage> promptMessages,
            List<ChatModelClient.ChatMessage> historyMessages
    ) {
        if (!StringUtils.hasText(conversationId)) {
            throw new BusinessException("CHAT_CONVERSATION_ID_INVALID", "会话记忆 conversationId 不能为空", HttpStatus.BAD_REQUEST);
        }
        if (promptMessages == null || promptMessages.isEmpty()) {
            throw new BusinessException("CHAT_PROMPT_EMPTY", "聊天提示消息不能为空", HttpStatus.BAD_REQUEST);
        }

        seedConversationMemoryIfNecessary(conversationId, historyMessages);

        var chatModel = springAiModelFactory.createChatModel(providerCode, modelCode);
        ChatClient chatClient = buildChatClient(chatModel);
        return chatClient.prompt()
                .messages(SpringAiModelSupport.toSpringMessages(promptMessages))
                .advisors(spec -> spec
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .chatResponse();
    }

    ChatClient buildChatClient(org.springframework.ai.chat.model.ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(buildDefaultAdvisors())
                .build();
    }

    List<Advisor> buildDefaultAdvisors() {
        List<Advisor> advisors = new ArrayList<>();
        advisors.add(MessageChatMemoryAdvisor.builder(chatMemory).build());

        if (chatClientAdvisorProperties.isSimpleLoggerAdvisorEnabled()) {
            // 放在 memory advisor 之后，确保日志里看到的是已经注入会话记忆后的最终请求。
            advisors.add(SimpleLoggerAdvisor.builder()
                    .order(Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER + 10)
                    .build());
        }

        return List.copyOf(advisors);
    }

    private void seedConversationMemoryIfNecessary(
            String conversationId,
            List<ChatModelClient.ChatMessage> historyMessages
    ) {
        if (historyMessages == null || historyMessages.isEmpty()) {
            return;
        }
        if (!chatMemory.get(conversationId).isEmpty()) {
            return;
        }
        chatMemory.add(conversationId, SpringAiModelSupport.toSpringMessages(historyMessages));
    }
}
