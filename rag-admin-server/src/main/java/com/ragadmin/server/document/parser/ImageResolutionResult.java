package com.ragadmin.server.document.parser;

public record ImageResolutionResult(
        String markdown,
        ImageProcessingReport report
) {
}
