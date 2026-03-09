package com.ragadmin.server.chat.dto;

public record ChatUsageResponse(Integer promptTokens, Integer completionTokens) {
}
