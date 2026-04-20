package com.ragadmin.server.document.parser;

public record DocumentSignals(
        boolean repeatedHeaderDetected,
        boolean repeatedFooterDetected,
        boolean tooManyBlankLines,
        boolean weakParagraphStructure,
        boolean ocrNoiseDetected,
        boolean symbolDensityHigh,
        boolean tocOutlineMissing
) {

    public static DocumentSignals empty() {
        return new DocumentSignals(false, false, false, false, false, false, false);
    }
}
