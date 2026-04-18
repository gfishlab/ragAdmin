package com.ragadmin.server.task.dto;

import java.time.LocalDateTime;

public record TaskStepResponse(
        String stepCode,
        String stepName,
        String stepStatus,
        String errorMessage,
        String detailJson,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
