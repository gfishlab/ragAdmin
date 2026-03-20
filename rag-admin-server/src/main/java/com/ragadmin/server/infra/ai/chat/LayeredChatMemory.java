package com.ragadmin.server.infra.ai.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ragadmin.server.chat.entity.ChatSessionMemorySummaryEntity;
import com.ragadmin.server.chat.mapper.ChatSessionMemorySummaryMapper;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LayeredChatMemory implements ChatMemory {

    private final JdbcChatMemoryRepository jdbcChatMemoryRepository;

    private final RedisShortTermChatMemoryStore redisShortTermChatMemoryStore;

    private final ChatSessionMemorySummaryMapper chatSessionMemorySummaryMapper;

    private final ChatMemoryProperties chatMemoryProperties;

    public LayeredChatMemory(
            JdbcChatMemoryRepository jdbcChatMemoryRepository,
            RedisShortTermChatMemoryStore redisShortTermChatMemoryStore,
            ChatSessionMemorySummaryMapper chatSessionMemorySummaryMapper,
            ChatMemoryProperties chatMemoryProperties
    ) {
        this.jdbcChatMemoryRepository = jdbcChatMemoryRepository;
        this.redisShortTermChatMemoryStore = redisShortTermChatMemoryStore;
        this.chatSessionMemorySummaryMapper = chatSessionMemorySummaryMapper;
        this.chatMemoryProperties = chatMemoryProperties;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        Assert.notNull(messages, "messages cannot be null");
        Assert.noNullElements(messages, "messages cannot contain null elements");

        List<Message> allMessages = processLongTermMessages(
                jdbcChatMemoryRepository.findByConversationId(conversationId),
                messages
        );
        jdbcChatMemoryRepository.saveAll(conversationId, allMessages);

        String summaryText = loadSummaryText(conversationId);
        redisShortTermChatMemoryStore.save(
                conversationId,
                redisShortTermChatMemoryStore.build(summaryText, extractRecentMessages(allMessages))
        );
    }

    @Override
    public List<Message> get(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");

        String summaryText = loadSummaryText(conversationId);
        RedisShortTermChatMemoryStore.StoredConversationMemory cached = redisShortTermChatMemoryStore.get(conversationId);
        if (cached != null) {
            return composeMemoryMessages(
                    StringUtils.hasText(cached.getSummaryText()) ? cached.getSummaryText() : summaryText,
                    redisShortTermChatMemoryStore.toSpringMessages(cached.getRecentMessages())
            );
        }

        List<Message> recentMessages = extractRecentMessages(jdbcChatMemoryRepository.findByConversationId(conversationId));
        if (!recentMessages.isEmpty() || StringUtils.hasText(summaryText)) {
            redisShortTermChatMemoryStore.save(
                    conversationId,
                    redisShortTermChatMemoryStore.build(summaryText, recentMessages)
            );
        }
        return composeMemoryMessages(summaryText, recentMessages);
    }

    @Override
    public void clear(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        jdbcChatMemoryRepository.deleteByConversationId(conversationId);
        redisShortTermChatMemoryStore.delete(conversationId);
        chatSessionMemorySummaryMapper.delete(new LambdaUpdateWrapper<ChatSessionMemorySummaryEntity>()
                .eq(ChatSessionMemorySummaryEntity::getConversationId, conversationId));
    }

    private String loadSummaryText(String conversationId) {
        ChatSessionMemorySummaryEntity summary = chatSessionMemorySummaryMapper.selectOne(
                new LambdaQueryWrapper<ChatSessionMemorySummaryEntity>()
                        .eq(ChatSessionMemorySummaryEntity::getConversationId, conversationId)
                        .last("LIMIT 1")
        );
        return summary == null ? null : summary.getSummaryText();
    }

    private List<Message> composeMemoryMessages(String summaryText, List<Message> recentMessages) {
        List<Message> result = new ArrayList<>();
        if (StringUtils.hasText(summaryText)) {
            result.add(new SystemMessage("以下是当前会话的历史摘要，请在回答时保持上下文一致：\n" + summaryText));
        }
        if (recentMessages != null && !recentMessages.isEmpty()) {
            result.addAll(recentMessages);
        }
        return result;
    }

    private List<Message> processLongTermMessages(List<Message> memoryMessages, List<Message> newMessages) {
        List<Message> processedMessages = new ArrayList<>();
        Set<Message> memoryMessagesSet = new HashSet<>(memoryMessages);
        boolean hasNewSystemMessage = newMessages.stream()
                .filter(SystemMessage.class::isInstance)
                .anyMatch(message -> !memoryMessagesSet.contains(message));

        memoryMessages.stream()
                .filter(message -> !(hasNewSystemMessage && message instanceof SystemMessage))
                .forEach(processedMessages::add);
        processedMessages.addAll(newMessages);
        return processedMessages;
    }

    private List<Message> extractRecentMessages(List<Message> allMessages) {
        if (allMessages == null || allMessages.isEmpty()) {
            return List.of();
        }
        List<Message> nonSystemMessages = allMessages.stream()
                .filter(message -> !(message instanceof SystemMessage))
                .toList();
        int messageLimit = Math.max(1, chatMemoryProperties.getShortTermMessageLimit());
        if (nonSystemMessages.size() <= messageLimit) {
            return nonSystemMessages;
        }
        return nonSystemMessages.subList(nonSystemMessages.size() - messageLimit, nonSystemMessages.size());
    }
}
