package com.ragadmin.server.chat.dto;

public record ChatSessionResponse(
        Long id,
        Long kbId,
        String sessionName,
        String status
) {
}
