package com.ragadmin.server.document.parser;

public record OcrCapability(
        boolean enabled,
        boolean available,
        String message,
        String language,
        int maxPdfPages
) {
}
