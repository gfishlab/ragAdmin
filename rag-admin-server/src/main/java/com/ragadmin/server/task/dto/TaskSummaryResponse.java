package com.ragadmin.server.task.dto;

public record TaskSummaryResponse(
        long total,
        long waiting,
        long running,
        long success,
        long failed,
        long canceled
) {
}
