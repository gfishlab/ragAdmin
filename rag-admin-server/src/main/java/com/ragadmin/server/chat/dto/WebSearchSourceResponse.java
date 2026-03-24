package com.ragadmin.server.chat.dto;

import java.time.Instant;

public record WebSearchSourceResponse(
        String title,
        String url,
        Instant publishedAt,
        String snippet
) {
}
