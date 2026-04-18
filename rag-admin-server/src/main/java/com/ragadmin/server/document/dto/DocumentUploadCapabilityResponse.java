package com.ragadmin.server.document.dto;

import java.util.List;

public record DocumentUploadCapabilityResponse(
        boolean ocrEnabled,
        boolean ocrAvailable,
        String ocrMessage,
        String ocrProvider,
        String ocrLanguage,
        int ocrMaxPdfPages,
        List<String> supportedDocTypes,
        List<String> ocrImageDocTypes
) {
}
