package com.ragadmin.server.document.parser;

import java.util.Collections;
import java.util.List;

public record ImageProcessingReport(
        int totalReferences,
        int resolvedCount,
        int failedCount,
        List<String> warnings
) {

    public static ImageProcessingReport empty() {
        return new ImageProcessingReport(0, 0, 0, Collections.emptyList());
    }

    public ImageProcessingReport {
        warnings = warnings == null ? Collections.emptyList() : Collections.unmodifiableList(warnings);
    }
}
