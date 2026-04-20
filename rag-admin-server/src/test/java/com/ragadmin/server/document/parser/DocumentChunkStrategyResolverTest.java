package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.entity.DocumentEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentChunkStrategyResolverTest {

    @Test
    void shouldSelectFirstMatchingStrategy() {
        DocumentChunkStrategy fallback = new RecursiveFallbackStrategy();
        DocumentChunkStrategyResolver resolver = new DocumentChunkStrategyResolver(List.of(fallback));

        ChunkContext context = new ChunkContext(null, DocumentSignals.empty(), ChunkStrategyProperties.defaults(), "TEXT");
        DocumentChunkStrategy result = resolver.resolve(context);

        assertSame(fallback, result);
    }

    @Test
    void shouldThrowWhenNoStrategyMatches() {
        DocumentEntity doc = new DocumentEntity();
        doc.setDocType("PDF");
        DocumentChunkStrategy neverMatch = new DocumentChunkStrategy() {
            @Override
            public boolean supports(ChunkContext context) {
                return false;
            }

            @Override
            public List<ChunkDraft> chunk(List<org.springframework.ai.document.Document> documents, ChunkContext context) {
                return List.of();
            }
        };

        DocumentChunkStrategyResolver resolver = new DocumentChunkStrategyResolver(List.of(neverMatch));
        ChunkContext context = new ChunkContext(doc, DocumentSignals.empty(), ChunkStrategyProperties.defaults(), "TEXT");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> resolver.resolve(context));
        assertTrue(ex.getMessage().contains("无匹配的分块策略"));
    }
}
