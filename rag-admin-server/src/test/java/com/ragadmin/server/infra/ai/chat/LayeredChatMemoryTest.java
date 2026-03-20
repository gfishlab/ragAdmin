package com.ragadmin.server.infra.ai.chat;

import com.ragadmin.server.chat.entity.ChatSessionMemorySummaryEntity;
import com.ragadmin.server.chat.mapper.ChatSessionMemorySummaryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LayeredChatMemoryTest {

    @Mock
    private JdbcChatMemoryRepository jdbcChatMemoryRepository;

    @Mock
    private RedisShortTermChatMemoryStore redisShortTermChatMemoryStore;

    @Mock
    private ChatSessionMemorySummaryMapper chatSessionMemorySummaryMapper;

    private LayeredChatMemory layeredChatMemory;

    @BeforeEach
    void setUp() {
        ChatMemoryProperties chatMemoryProperties = new ChatMemoryProperties();
        chatMemoryProperties.setShortTermRounds(1);
        layeredChatMemory = new LayeredChatMemory(
                jdbcChatMemoryRepository,
                redisShortTermChatMemoryStore,
                chatSessionMemorySummaryMapper,
                chatMemoryProperties
        );
    }

    @Test
    void shouldReturnSummaryAndCachedRecentMessages() {
        RedisShortTermChatMemoryStore.StoredConversationMemory cached = new RedisShortTermChatMemoryStore.StoredConversationMemory(
                "这是摘要",
                List.of(
                        new RedisShortTermChatMemoryStore.StoredMessage("USER", "问题"),
                        new RedisShortTermChatMemoryStore.StoredMessage("ASSISTANT", "答案")
                ),
                10,
                "2026-03-20T12:00:00Z"
        );
        when(chatSessionMemorySummaryMapper.selectOne(any())).thenReturn(null);
        when(redisShortTermChatMemoryStore.get("conversation-1")).thenReturn(cached);
        when(redisShortTermChatMemoryStore.toSpringMessages(cached.getRecentMessages())).thenReturn(List.of(
                new UserMessage("问题"),
                new AssistantMessage("答案")
        ));

        List<Message> messages = layeredChatMemory.get("conversation-1");

        assertEquals(3, messages.size());
        assertInstanceOf(SystemMessage.class, messages.get(0));
        assertTrue(messages.get(0).getText().contains("这是摘要"));
        assertEquals("问题", messages.get(1).getText());
        assertEquals("答案", messages.get(2).getText());
    }

    @Test
    void shouldPersistFullHistoryAndUpdateRecentCacheWhenAddMessages() {
        when(jdbcChatMemoryRepository.findByConversationId("conversation-1")).thenReturn(List.of(
                new UserMessage("旧问题"),
                new AssistantMessage("旧答案")
        ));

        ArgumentCaptor<List<Message>> messageCaptor = ArgumentCaptor.forClass(List.class);

        layeredChatMemory.add("conversation-1", List.of(
                new UserMessage("新问题"),
                new AssistantMessage("新答案")
        ));

        verify(jdbcChatMemoryRepository).saveAll(eq("conversation-1"), messageCaptor.capture());
        assertEquals(4, messageCaptor.getValue().size());
        verify(redisShortTermChatMemoryStore).save(eq("conversation-1"), any());
    }

    @Test
    void shouldClearJdbcRedisAndSummaryTogether() {
        layeredChatMemory.clear("conversation-1");

        verify(jdbcChatMemoryRepository).deleteByConversationId("conversation-1");
        verify(redisShortTermChatMemoryStore).delete("conversation-1");
        verify(chatSessionMemorySummaryMapper).delete(any());
    }

    @Test
    void shouldLoadSummaryFromSummaryTableWhenCacheMiss() {
        ChatSessionMemorySummaryEntity summary = new ChatSessionMemorySummaryEntity();
        summary.setSummaryText("长期摘要");
        when(chatSessionMemorySummaryMapper.selectOne(any())).thenReturn(summary);
        when(redisShortTermChatMemoryStore.get("conversation-1")).thenReturn(null);
        when(jdbcChatMemoryRepository.findByConversationId("conversation-1")).thenReturn(List.of(
                new UserMessage("最近问题"),
                new AssistantMessage("最近答案")
        ));

        List<Message> messages = layeredChatMemory.get("conversation-1");

        assertEquals(3, messages.size());
        assertTrue(messages.get(0).getText().contains("长期摘要"));
        verify(redisShortTermChatMemoryStore).save(eq("conversation-1"), any());
    }
}
