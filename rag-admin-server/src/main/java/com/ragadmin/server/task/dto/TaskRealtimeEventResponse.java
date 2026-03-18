package com.ragadmin.server.task.dto;

import java.time.LocalDateTime;

public record TaskRealtimeEventResponse(
        String eventType,
        Long taskId,
        Long kbId,
        Long documentId,
        String documentName,
        String taskStatus,
        String parseStatus,
        String currentStepCode,
        String currentStepName,
        Integer progressPercent,
        String message,
        Boolean terminal,
        LocalDateTime occurredAt
) {
}
