package com.ragadmin.server.infra.ai.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class RedisShortTermChatMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(RedisShortTermChatMemoryStore.class);

    private static final TypeReference<StoredConversationMemory> STORED_MEMORY_TYPE = new TypeReference<>() {
    };

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatMemoryProperties chatMemoryProperties;

    public StoredConversationMemory get(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return null;
        }
        String json = stringRedisTemplate.opsForValue().get(buildShortKey(conversationId));
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, STORED_MEMORY_TYPE);
        } catch (JsonProcessingException ex) {
            log.warn("解析 Redis 短期会话记忆失败，conversationId={}", conversationId, ex);
            delete(conversationId);
            return null;
        }
    }

    public void save(String conversationId, StoredConversationMemory memory) {
        if (!StringUtils.hasText(conversationId) || memory == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(
                    buildShortKey(conversationId),
                    objectMapper.writeValueAsString(memory),
                    Duration.ofMinutes(Math.max(1, chatMemoryProperties.getShortTermIdleTtlMinutes()))
            );
        } catch (JsonProcessingException ex) {
            log.warn("写入 Redis 短期会话记忆失败，conversationId={}", conversationId, ex);
        }
    }

    public void delete(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return;
        }
        stringRedisTemplate.delete(buildShortKey(conversationId));
        stringRedisTemplate.delete(buildLockKey(conversationId));
        stringRedisTemplate.delete(buildWarmKey(conversationId));
    }

    public String buildLockKey(String conversationId) {
        return chatMemoryProperties.getRedisKeyPrefix() + ":lock:" + conversationId;
    }

    public String buildWarmKey(String conversationId) {
        return chatMemoryProperties.getRedisKeyPrefix() + ":warm:" + conversationId;
    }

    public int estimateTokens(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        return messages.stream()
                .map(Message::getText)
                .filter(StringUtils::hasText)
                .mapToInt(text -> Math.max(1, text.length() / 4))
                .sum();
    }

    public List<StoredMessage> toStoredMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream()
                .map(message -> new StoredMessage(message.getMessageType().name(), message.getText()))
                .toList();
    }

    public List<Message> toSpringMessages(List<StoredMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream()
                .map(this::toSpringMessage)
                .toList();
    }

    public StoredConversationMemory build(String summaryText, List<Message> messages) {
        return new StoredConversationMemory(
                summaryText,
                toStoredMessages(messages),
                estimateTokens(messages),
                Instant.now().toString()
        );
    }

    private String buildShortKey(String conversationId) {
        return chatMemoryProperties.getRedisKeyPrefix() + ":short:" + conversationId;
    }

    private Message toSpringMessage(StoredMessage message) {
        if (message == null) {
            return new UserMessage("");
        }
        String type = message.getMessageType() == null ? "USER" : message.getMessageType().trim().toUpperCase();
        return switch (type) {
            case "ASSISTANT" -> new AssistantMessage(message.getText());
            case "SYSTEM" -> new SystemMessage(message.getText());
            case "TOOL" -> ToolResponseMessage.builder().responses(List.of()).build();
            default -> new UserMessage(message.getText());
        };
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StoredConversationMemory {

        private String summaryText;
        private List<StoredMessage> recentMessages;
        private Integer estimatedTokens;
        private String lastActiveAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StoredMessage {

        private String messageType;
        private String text;
    }
}
