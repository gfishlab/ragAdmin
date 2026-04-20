package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.entity.DocumentEntity;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PdfTextChunkStrategyTest {

    private final PdfTextChunkStrategy strategy = new PdfTextChunkStrategy();

    private ChunkContext pdfTextContext(int maxChunkChars, int overlapChars) {
        DocumentEntity doc = new DocumentEntity();
        doc.setDocType("PDF");
        return new ChunkContext(doc, DocumentSignals.empty(), new ChunkStrategyProperties(maxChunkChars, overlapChars, 50), "TEXT");
    }

    private ChunkContext pdfOcrContext() {
        DocumentEntity doc = new DocumentEntity();
        doc.setDocType("PDF");
        return new ChunkContext(doc, DocumentSignals.empty(), ChunkStrategyProperties.defaults(), "OCR");
    }

    private ChunkContext nonPdfContext() {
        DocumentEntity doc = new DocumentEntity();
        doc.setDocType("MD");
        return new ChunkContext(doc, DocumentSignals.empty(), ChunkStrategyProperties.defaults(), "TEXT");
    }

    @Nested
    class Supports {

        @Test
        void shouldMatchPdfText() {
            assertTrue(strategy.supports(pdfTextContext(800, 120)));
        }

        @Test
        void shouldNotMatchPdfOcr() {
            assertFalse(strategy.supports(pdfOcrContext()));
        }

        @Test
        void shouldNotMatchNonPdf() {
            assertFalse(strategy.supports(nonPdfContext()));
        }
    }

    @Nested
    class TableDetection {

        @Test
        void shouldDetectPipeTable() {
            String table = "| 姓名 | 年龄 | 城市 |\n| --- | --- | --- |\n| 张三 | 28 | 北京 |";
            assertTrue(strategy.isTableBlock(table));
        }

        @Test
        void shouldDetectTabTable() {
            String table = "姓名\t年龄\t城市\n张三\t28\t北京\n李四\t30\t上海";
            assertTrue(strategy.isTableBlock(table));
        }

        @Test
        void shouldNotDetectPlainText() {
            assertFalse(strategy.isTableBlock("这是普通文本，不是表格"));
        }
    }

    @Nested
    class Chunk {

        @Test
        void shouldChunkPdfTextContent() {
            String content = "段落一，PDF文本解析结果。\n\n" + "b".repeat(300) + "\n\n段落三内容";
            List<ChunkDraft> chunks = strategy.chunk(
                    List.of(new Document(content, Map.of("parseMode", "TEXT"))),
                    pdfTextContext(200, 40));

            assertFalse(chunks.isEmpty());
            assertEquals("TEXT", chunks.getFirst().metadata().get("parseMode"));
        }

        @Test
        void shouldKeepTableIntact() {
            String table = "| 项目 | 2023 | 2024 |\n| --- | --- | --- |\n| 收入 | 100 | 200 |\n| 利润 | 50 | 120 |";
            String content = "前置段落内容\n\n" + table + "\n\n后续段落内容";
            List<ChunkDraft> chunks = strategy.chunk(
                    List.of(new Document(content, Map.of())),
                    pdfTextContext(800, 120));

            boolean hasTableChunk = chunks.stream()
                    .anyMatch(c -> c.text().contains("| 项目") && c.text().contains("| 利润"));
            assertTrue(hasTableChunk, "表格应完整保留在切片中");
        }

        @Test
        void shouldPreserveMetadata() {
            List<ChunkDraft> chunks = strategy.chunk(
                    List.of(new Document("内容", Map.of("readerType", "PDF_PARAGRAPH_READER", "pageNo", 1))),
                    pdfTextContext(800, 120));

            assertEquals(1, chunks.size());
            assertEquals("PDF_PARAGRAPH_READER", chunks.getFirst().metadata().get("readerType"));
            assertEquals(1, chunks.getFirst().metadata().get("pageNo"));
        }
    }
}
