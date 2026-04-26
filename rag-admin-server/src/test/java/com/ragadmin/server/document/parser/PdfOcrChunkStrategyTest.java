package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.entity.DocumentEntity;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PdfOcrChunkStrategyTest {

    private final PdfOcrChunkStrategy strategy = new PdfOcrChunkStrategy();

    private ChunkContext ocrContext(int maxChunkChars, int overlapChars) {
        DocumentEntity doc = new DocumentEntity();
        doc.setDocType("PDF");
        return ChunkContext.of(doc, DocumentSignals.empty(), new ChunkStrategyProperties(maxChunkChars, overlapChars, 50), "OCR");
    }

    private ChunkContext textModeContext() {
        DocumentEntity doc = new DocumentEntity();
        doc.setDocType("PDF");
        return ChunkContext.of(doc, DocumentSignals.empty(), ChunkStrategyProperties.defaults(), "TEXT");
    }

    private ChunkContext nonPdfContext() {
        DocumentEntity doc = new DocumentEntity();
        doc.setDocType("MD");
        return ChunkContext.of(doc, DocumentSignals.empty(), ChunkStrategyProperties.defaults(), "OCR");
    }

    @Nested
    class Supports {

        @Test
        void shouldMatchPdfOcr() {
            assertTrue(strategy.supports(ocrContext(800, 120)));
        }

        @Test
        void shouldNotMatchPdfText() {
            assertFalse(strategy.supports(textModeContext()));
        }

        @Test
        void shouldNotMatchNonPdf() {
            assertFalse(strategy.supports(nonPdfContext()));
        }
    }

    @Nested
    class MergeWeakParagraphs {

        @Test
        void shouldMergeShortLinesIntoParagraphs() {
            String weak = "姓名 张三\n年龄 28\n学历 本科\n\n工作经历\n公司A 工程师\n公司B 架构师";
            String merged = strategy.mergeWeakParagraphs(weak);

            assertFalse(merged.contains("姓名 张三\n年龄"));
            assertTrue(merged.contains("姓名 张三 年龄 28 学历 本科"));
        }

        @Test
        void shouldRespectDoubleNewlines() {
            String text = "段落一\n\n段落二";
            String merged = strategy.mergeWeakParagraphs(text);

            assertTrue(merged.contains("段落一\n\n段落二"));
        }
    }

    @Nested
    class Chunk {

        @Test
        void shouldChunkOcrTextWithWeakParagraphs() {
            DocumentSignals signals = new DocumentSignals(false, false, false, true, false, false, false, false, false, 0.0, 0.0, 0.1, 0.05);
            DocumentEntity doc = new DocumentEntity();
            doc.setDocType("PDF");
            ChunkContext ctx = ChunkContext.of(doc, signals, new ChunkStrategyProperties(200, 40, 50), "OCR");

            String content = "姓名 张三\n年龄 28\n\n工作经历\n公司A 高级工程师\n2024-至今\n\n教育背景\n北京大学 计算机科学";
            List<ChunkDraft> chunks = strategy.chunk(
                    List.of(new Document(content, Map.of("parseMode", "OCR"))),
                    ctx);

            assertFalse(chunks.isEmpty());
            assertEquals("OCR", chunks.getFirst().metadata().get("parseMode"));
        }

        @Test
        void shouldChunkOcrTextWithoutWeakParagraphs() {
            String content = "段落一内容，有完整的段落结构。\n\n" + "b".repeat(300) + "\n\n段落三";
            List<ChunkDraft> chunks = strategy.chunk(
                    List.of(new Document(content, Map.of())),
                    ocrContext(200, 40));

            assertFalse(chunks.isEmpty());
        }
    }
}
