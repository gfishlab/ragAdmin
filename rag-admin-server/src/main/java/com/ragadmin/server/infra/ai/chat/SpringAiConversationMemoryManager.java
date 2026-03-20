package com.ragadmin.server.infra.ai.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ragadmin.server.chat.entity.ChatMessageEntity;
import com.ragadmin.server.chat.entity.ChatSessionMemorySummaryEntity;
import com.ragadmin.server.chat.mapper.ChatMessageMapper;
import com.ragadmin.server.chat.mapper.ChatSessionMemorySummaryMapper;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class SpringAiConversationMemoryManager implements ConversationMemoryManager {

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ChatSessionMemorySummaryMapper chatSessionMemorySummaryMapper;

    @Autowired
    private RedisShortTermChatMemoryStore redisShortTermChatMemoryStore;

    @Autowired
    private ConversationIdCodec conversationIdCodec;

    @Autowired
    private ChatMemoryProperties chatMemoryProperties;

    @Override
    public void clear(String conversationId) {
        chatMemory.clear(conversationId);
    }

    @Override
    public void refresh(String conversationId) {
        Long sessionId = conversationIdCodec.parseSessionId(conversationId);
        if (sessionId == null) {
            return;
        }

        List<ChatMessageEntity> exchanges = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, sessionId)
                .orderByAsc(ChatMessageEntity::getId));
        if (exchanges.isEmpty()) {
            redisShortTermChatMemoryStore.delete(conversationId);
            chatSessionMemorySummaryMapper.delete(new LambdaUpdateWrapper<ChatSessionMemorySummaryEntity>()
                    .eq(ChatSessionMemorySummaryEntity::getConversationId, conversationId));
            return;
        }

        String summaryText = buildSummaryText(exchanges);
        upsertSummary(sessionId, conversationId, summaryText, exchanges);
        redisShortTermChatMemoryStore.save(
                conversationId,
                redisShortTermChatMemoryStore.build(summaryText, buildRecentMessages(exchanges))
        );
    }

    private void upsertSummary(
            Long sessionId,
            String conversationId,
            String summaryText,
            List<ChatMessageEntity> exchanges
    ) {
        ChatSessionMemorySummaryEntity existing = chatSessionMemorySummaryMapper.selectOne(
                new LambdaQueryWrapper<ChatSessionMemorySummaryEntity>()
                        .eq(ChatSessionMemorySummaryEntity::getConversationId, conversationId)
                        .last("LIMIT 1")
        );
        if (!StringUtils.hasText(summaryText)) {
            if (existing != null) {
                chatSessionMemorySummaryMapper.deleteById(existing.getId());
            }
            return;
        }

        ChatSessionMemorySummaryEntity entity = existing == null ? new ChatSessionMemorySummaryEntity() : existing;
        entity.setSessionId(sessionId);
        entity.setConversationId(conversationId);
        entity.setSummaryText(summaryText);
        entity.setCompressedMessageCount(Math.max(0, exchanges.size() - Math.max(1, chatMemoryProperties.getShortTermRounds())));
        entity.setCompressedUntilMessageId(resolveCompressedUntilMessageId(exchanges));
        entity.setLastSourceMessageId(exchanges.get(exchanges.size() - 1).getId());
        entity.setUpdatedAt(LocalDateTime.now());
        if (existing == null) {
            entity.setSummaryVersion(1);
            chatSessionMemorySummaryMapper.insert(entity);
            return;
        }
        entity.setSummaryVersion(Math.max(1, entity.getSummaryVersion()) + 1);
        chatSessionMemorySummaryMapper.updateById(entity);
    }

    private Long resolveCompressedUntilMessageId(List<ChatMessageEntity> exchanges) {
        int keepRounds = Math.max(1, chatMemoryProperties.getShortTermRounds());
        if (exchanges.size() <= keepRounds) {
            return null;
        }
        return exchanges.get(exchanges.size() - keepRounds - 1).getId();
    }

    private List<Message> buildRecentMessages(List<ChatMessageEntity> exchanges) {
        int keepRounds = Math.max(1, chatMemoryProperties.getShortTermRounds());
        List<ChatMessageEntity> recentExchanges = exchanges.size() <= keepRounds
                ? exchanges
                : exchanges.subList(exchanges.size() - keepRounds, exchanges.size());
        List<Message> messages = new ArrayList<>(recentExchanges.size() * 2);
        for (ChatMessageEntity exchange : recentExchanges) {
            if (StringUtils.hasText(exchange.getQuestionText())) {
                messages.add(new UserMessage(exchange.getQuestionText()));
            }
            if (StringUtils.hasText(exchange.getAnswerText())) {
                messages.add(new AssistantMessage(exchange.getAnswerText()));
            }
        }
        return messages;
    }

    private String buildSummaryText(List<ChatMessageEntity> exchanges) {
        int keepRounds = Math.max(1, chatMemoryProperties.getShortTermRounds());
        if (exchanges.size() <= keepRounds) {
            return null;
        }
        List<ChatMessageEntity> olderExchanges = exchanges.subList(0, exchanges.size() - keepRounds);
        StringBuilder builder = new StringBuilder("历史对话摘要：\n");
        int maxLength = Math.max(200, chatMemoryProperties.getSummaryMaxLength());
        for (ChatMessageEntity exchange : olderExchanges) {
            appendLine(builder, "用户：", exchange.getQuestionText(), maxLength);
            appendLine(builder, "助手：", exchange.getAnswerText(), maxLength);
            if (builder.length() >= maxLength) {
                break;
            }
        }
        String summary = builder.toString().trim();
        if (summary.length() <= maxLength) {
            return summary;
        }
        return summary.substring(0, maxLength - 3) + "...";
    }

    private void appendLine(StringBuilder builder, String prefix, String text, int maxLength) {
        if (!StringUtils.hasText(text) || builder.length() >= maxLength) {
            return;
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        int remaining = maxLength - builder.length();
        if (remaining <= prefix.length() + 1) {
            return;
        }
        int textLimit = Math.min(normalized.length(), Math.max(16, remaining - prefix.length() - 1));
        builder.append(prefix).append(normalized, 0, textLimit);
        if (textLimit < normalized.length()) {
            builder.append("...");
        }
        builder.append('\n');
    }
}
