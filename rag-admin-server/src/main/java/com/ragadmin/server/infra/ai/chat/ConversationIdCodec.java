package com.ragadmin.server.infra.ai.chat;

import com.ragadmin.server.chat.ChatSceneTypes;
import com.ragadmin.server.chat.ChatTerminalTypes;
import com.ragadmin.server.chat.entity.ChatSessionEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ConversationIdCodec {

    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("-session-(\\d+)$");

    public String encode(ChatSessionEntity session) {
        return "chat-terminal-" + normalizeTerminalType(session.getTerminalType()).toLowerCase()
                + "-scene-" + normalizeSceneType(session.getSceneType()).toLowerCase()
                + "-user-" + session.getUserId()
                + "-session-" + session.getId();
    }

    public Long parseSessionId(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return null;
        }
        Matcher matcher = SESSION_ID_PATTERN.matcher(conversationId);
        if (!matcher.find()) {
            return null;
        }
        return Long.parseLong(matcher.group(1));
    }

    private String normalizeTerminalType(String terminalType) {
        if (!StringUtils.hasText(terminalType)) {
            return ChatTerminalTypes.ADMIN;
        }
        return terminalType.trim().toUpperCase();
    }

    private String normalizeSceneType(String sceneType) {
        if (!StringUtils.hasText(sceneType)) {
            return ChatSceneTypes.KNOWLEDGE_BASE;
        }
        return sceneType.trim().toUpperCase();
    }
}
