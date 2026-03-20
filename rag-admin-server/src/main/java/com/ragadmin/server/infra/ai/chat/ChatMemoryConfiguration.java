package com.ragadmin.server.infra.ai.chat;

import com.ragadmin.server.chat.mapper.ChatSessionMemorySummaryMapper;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class ChatMemoryConfiguration {

    /**
     * 显式声明 JDBC Chat Memory 仓储，避免依赖自动装配时的 bean 命名细节。
     */
    @Bean
    public JdbcChatMemoryRepository jdbcChatMemoryRepository(DataSource dataSource) {
        return JdbcChatMemoryRepository.builder()
                .dataSource(dataSource)
                .build();
    }

    /**
     * 会话记忆统一走“PostgreSQL 长期记忆 + Redis 短期热记忆 + 摘要表”的混合编排。
     */
    @Bean
    public ChatMemory chatMemory(
            JdbcChatMemoryRepository jdbcChatMemoryRepository,
            RedisShortTermChatMemoryStore redisShortTermChatMemoryStore,
            ChatSessionMemorySummaryMapper chatSessionMemorySummaryMapper,
            ChatMemoryProperties chatMemoryProperties
    ) {
        return new LayeredChatMemory(
                jdbcChatMemoryRepository,
                redisShortTermChatMemoryStore,
                chatSessionMemorySummaryMapper,
                chatMemoryProperties
        );
    }
}
