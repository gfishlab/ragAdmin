package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.entity.DocumentEntity;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultCleanerPolicyResolverTest {

    private final DefaultCleanerPolicyResolver resolver = new DefaultCleanerPolicyResolver();

    @Test
    void shouldEnablePdfSemanticCleaningForTextMode() {
        DocumentEntity document = document("PDF");

        DocumentCleanPolicy policy = resolver.resolve(new DocumentCleaningRequest(
                document,
                List.of(new Document("正文", Map.of("parseMode", "TEXT", "readerType", "TIKA")))
        ));

        assertTrue(policy.safeCleanEnabled());
        assertTrue(policy.semanticCleanEnabled());
        assertTrue(policy.headerFooterCleanEnabled());
        assertTrue(policy.lineMergeEnabled());
        assertFalse(policy.ocrNoiseCleanEnabled());
    }

    @Test
    void shouldPreserveSymbolsForMarkdownAndEnableOcrNoiseForOcrMode() {
        DocumentCleanPolicy markdownPolicy = resolver.resolve(new DocumentCleaningRequest(
                document("MD"),
                List.of(new Document("# 标题\n- 项目", Map.of("parseMode", "TEXT", "readerType", "MARKDOWN")))
        ));
        assertTrue(markdownPolicy.preserveSymbols());
        assertFalse(markdownPolicy.semanticCleanEnabled());

        DocumentCleanPolicy ocrPolicy = resolver.resolve(new DocumentCleaningRequest(
                document("PDF"),
                List.of(new Document("扫描内容", Map.of("parseMode", "OCR", "readerType", "TIKA_PDF")))
        ));
        assertTrue(ocrPolicy.ocrNoiseCleanEnabled());
        assertFalse(ocrPolicy.lineMergeEnabled());
    }

    private DocumentEntity document(String docType) {
        DocumentEntity entity = new DocumentEntity();
        entity.setDocType(docType);
        return entity;
    }
}
