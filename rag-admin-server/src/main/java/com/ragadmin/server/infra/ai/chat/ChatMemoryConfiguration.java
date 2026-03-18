package com.ragadmin.server.infra.ai.chat;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatMemoryConfiguration {

    /**
     * 当前阶段采用“固定窗口”记忆策略，避免无限历史直接进入模型上下文。
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository, ChatMemoryProperties chatMemoryProperties) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(Math.max(1, chatMemoryProperties.getMaxMessages()))
                .build();
    }
}
