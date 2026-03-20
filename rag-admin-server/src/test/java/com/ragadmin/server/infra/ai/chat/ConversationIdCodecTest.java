package com.ragadmin.server.infra.ai.chat;

import com.ragadmin.server.chat.ChatSceneTypes;
import com.ragadmin.server.chat.ChatTerminalTypes;
import com.ragadmin.server.chat.entity.ChatSessionEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConversationIdCodecTest {

    private final ConversationIdCodec conversationIdCodec = new ConversationIdCodec();

    @Test
    void shouldEncodeConversationIdWithTerminalSceneUserAndSession() {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(22L);
        session.setUserId(1001L);
        session.setTerminalType(ChatTerminalTypes.APP);
        session.setSceneType(ChatSceneTypes.KNOWLEDGE_BASE);

        String conversationId = conversationIdCodec.encode(session);

        assertEquals("chat-terminal-app-scene-knowledge_base-user-1001-session-22", conversationId);
    }

    @Test
    void shouldParseSessionIdFromConversationId() {
        assertEquals(22L, conversationIdCodec.parseSessionId(
                "chat-terminal-app-scene-knowledge_base-user-1001-session-22"
        ));
    }

    @Test
    void shouldReturnNullWhenConversationIdDoesNotContainSessionId() {
        assertNull(conversationIdCodec.parseSessionId("chat-terminal-app-scene-general-user-1001"));
    }
}
