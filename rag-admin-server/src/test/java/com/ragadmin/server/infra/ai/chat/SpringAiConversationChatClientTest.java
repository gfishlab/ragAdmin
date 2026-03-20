package com.ragadmin.server.infra.ai.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SpringAiConversationChatClientTest {

    private SpringAiConversationChatClient chatClient;

    private ChatClientAdvisorProperties chatClientAdvisorProperties;

    @BeforeEach
    void setUp() {
        chatClient = new SpringAiConversationChatClient();
        chatClientAdvisorProperties = new ChatClientAdvisorProperties();
        ReflectionTestUtils.setField(chatClient, "chatMemory", mock(ChatMemory.class));
        ReflectionTestUtils.setField(chatClient, "chatClientAdvisorProperties", chatClientAdvisorProperties);
    }

    @Test
    void shouldBuildMemoryAdvisorOnlyWhenSimpleLoggerAdvisorDisabled() {
        chatClientAdvisorProperties.setSimpleLoggerAdvisorEnabled(false);

        List<Advisor> advisors = chatClient.buildDefaultAdvisors();

        assertEquals(1, advisors.size());
        assertInstanceOf(MessageChatMemoryAdvisor.class, advisors.getFirst());
    }

    @Test
    void shouldBuildSimpleLoggerAdvisorAfterMemoryAdvisorWhenEnabled() {
        chatClientAdvisorProperties.setSimpleLoggerAdvisorEnabled(true);

        List<Advisor> advisors = chatClient.buildDefaultAdvisors();

        assertEquals(2, advisors.size());
        assertInstanceOf(MessageChatMemoryAdvisor.class, advisors.get(0));
        assertInstanceOf(SimpleLoggerAdvisor.class, advisors.get(1));
        assertTrue(advisors.get(1).getOrder() > advisors.get(0).getOrder());
    }
}
