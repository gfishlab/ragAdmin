package com.ragadmin.server.document.parser;

public record DocumentSignals(
        boolean repeatedHeaderDetected,
        boolean repeatedFooterDetected,
        boolean tooManyBlankLines,
        boolean weakParagraphStructure,
        boolean ocrNoiseDetected,
        boolean symbolDensityHigh,
        boolean tocOutlineMissing,
        boolean markdownTableDetected,
        boolean markdownImageDetected,
        double tableRatio,
        double imageRatio
) {

    public static DocumentSignals empty() {
        return new DocumentSignals(false, false, false, false, false, false, false,
                false, false, 0.0, 0.0);
    }

    public boolean containsTable() {
        return markdownTableDetected || tableRatio > 0.1;
    }

    public boolean containsImage() {
        return markdownImageDetected || imageRatio > 0.05;
    }

    public String inferContentType() {
        boolean hasTable = containsTable();
        boolean hasImage = containsImage();
        if (hasTable && hasImage) return "MIXED";
        if (hasTable) return "TABLE";
        if (hasImage) return "IMAGE";
        return "TEXT";
    }
}
