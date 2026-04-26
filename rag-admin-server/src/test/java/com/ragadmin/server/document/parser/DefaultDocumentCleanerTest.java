package com.ragadmin.server.document.parser;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultDocumentCleanerTest {

    private final CleanerPolicyResolver cleanerPolicyResolver = mock(CleanerPolicyResolver.class);
    private final DocumentSignalAnalyzer signalAnalyzer = new DefaultDocumentSignalAnalyzer();
    private final DefaultDocumentCleaner cleaner = new DefaultDocumentCleaner(
            signalAnalyzer,
            cleanerPolicyResolver,
            List.of(new SafeNormalizationCleaner(), new SemanticPreservingCleaner())
    );

    @Test
    void shouldNormalizeWhitespaceAndMarkMetadata() {
        when(cleanerPolicyResolver.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new DocumentCleanPolicy(true, false, true, false, false, false));
        List<Document> cleaned = cleaner.clean(List.of(new Document(
                "\r\n 第一段 \r\n\r\n\r\n第二段 \r\n  ",
                Map.of("readerType", "TIKA", "parseMode", "TEXT")
        )), new DocumentCleanContext(document("TXT"), new DocumentCleanPolicy(true, false, true, false, false, false)));

        assertEquals(1, cleaned.size());
        assertEquals("第一段\n第二段", cleaned.getFirst().getText());
        assertEquals("TIKA", cleaned.getFirst().getMetadata().get("readerType"));
        assertEquals(Boolean.TRUE, cleaned.getFirst().getMetadata().get("cleaned"));
        assertEquals("v1", cleaned.getFirst().getMetadata().get("cleanVersion"));
    }

    @Test
    void shouldDropBlankDocumentsAfterCleaning() {
        when(cleanerPolicyResolver.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new DocumentCleanPolicy(true, false, true, false, false, false));
        List<Document> cleaned = cleaner.clean(
                List.of(new Document(" \r\n \r\n ", Map.of("readerType", "TIKA"))),
                new DocumentCleanContext(document("TXT"), new DocumentCleanPolicy(true, false, true, false, false, false))
        );

        assertTrue(cleaned.isEmpty());
    }

    private com.ragadmin.server.document.entity.DocumentEntity document(String docType) {
        com.ragadmin.server.document.entity.DocumentEntity entity = new com.ragadmin.server.document.entity.DocumentEntity();
        entity.setDocType(docType);
        return entity;
    }
}
