package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.entity.DocumentEntity;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HtmlChunkStrategyTest {

    private final HtmlChunkStrategy strategy = new HtmlChunkStrategy();

    private ChunkContext htmlContext(int maxChunkChars, int overlapChars) {
        DocumentEntity doc = new DocumentEntity();
        doc.setDocType("HTML");
        return ChunkContext.of(doc, DocumentSignals.empty(), new ChunkStrategyProperties(maxChunkChars, overlapChars, 50), "TEXT");
    }

    private ChunkContext nonHtmlContext() {
        DocumentEntity doc = new DocumentEntity();
        doc.setDocType("PDF");
        return ChunkContext.of(doc, DocumentSignals.empty(), ChunkStrategyProperties.defaults(), "TEXT");
    }

    @Nested
    class Supports {

        @Test
        void shouldMatchHtmlDocType() {
            assertTrue(strategy.supports(htmlContext(800, 120)));
        }

        @Test
        void shouldMatchHtmDocType() {
            DocumentEntity doc = new DocumentEntity();
            doc.setDocType("HTM");
            ChunkContext ctx = ChunkContext.of(doc, DocumentSignals.empty(), ChunkStrategyProperties.defaults(), "TEXT");
            assertTrue(strategy.supports(ctx));
        }

        @Test
        void shouldNotMatchNonHtml() {
            assertFalse(strategy.supports(nonHtmlContext()));
        }
    }

    @Nested
    class Chunk {

        @Test
        void shouldChunkHtmlExtractedText() {
            String content = "段落一内容，这是从HTML提取的文本。\n\n" + "b".repeat(300) + "\n\n段落三内容";
            List<ChunkDraft> chunks = strategy.chunk(
                    List.of(new Document(content, Map.of("readerType", "HTML_READER"))),
                    htmlContext(200, 40));

            assertFalse(chunks.isEmpty());
            assertEquals("HTML_READER", chunks.getFirst().metadata().get("readerType"));
            assertNotNull(chunks.getFirst().metadata().get("parentDocumentId"));
        }

        @Test
        void shouldHandleMultipleDocuments() {
            List<ChunkDraft> chunks = strategy.chunk(
                    List.of(
                            new Document("第一页内容", Map.of("pageNo", 1)),
                            new Document("第二页内容", Map.of("pageNo", 2))
                    ),
                    htmlContext(800, 120));

            assertEquals(2, chunks.size());
            assertEquals(1, chunks.getFirst().metadata().get("pageNo"));
            assertEquals(2, chunks.get(1).metadata().get("pageNo"));
        }
    }
}
