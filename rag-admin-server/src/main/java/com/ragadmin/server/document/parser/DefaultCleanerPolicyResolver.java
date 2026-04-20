package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Component
public class DefaultCleanerPolicyResolver implements CleanerPolicyResolver {

    @Override
    public DocumentCleanPolicy resolve(DocumentCleaningRequest request) {
        String docType = normalizeDocType(request.document().getDocType());
        String parseMode = detectParseMode(request.documents());
        DocumentSignals signals = request.signals();

        boolean preserveSymbols = List.of("MD", "MARKDOWN", "HTML", "HTM").contains(docType) || "OCR".equals(parseMode);

        boolean headerFooter = ("PDF".equals(docType) && "TEXT".equals(parseMode))
                && (signals.repeatedHeaderDetected() || signals.repeatedFooterDetected());

        boolean lineMerge = ("PDF".equals(docType) && "TEXT".equals(parseMode))
                && signals.weakParagraphStructure();

        boolean ocrNoise = "OCR".equals(parseMode)
                && signals.ocrNoiseDetected();

        boolean semantic = headerFooter || lineMerge || ocrNoise;

        return new DocumentCleanPolicy(
                true,
                semantic,
                preserveSymbols,
                headerFooter,
                lineMerge,
                ocrNoise
        );
    }

    private String detectParseMode(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return "TEXT";
        }
        Object parseMode = documents.getFirst().getMetadata().get("parseMode");
        if (parseMode == null) {
            return "TEXT";
        }
        String value = parseMode.toString().trim();
        return StringUtils.hasText(value) ? value.toUpperCase(Locale.ROOT) : "TEXT";
    }

    private String normalizeDocType(String docType) {
        if (!StringUtils.hasText(docType)) {
            return "";
        }
        return docType.trim().toUpperCase(Locale.ROOT);
    }
}
