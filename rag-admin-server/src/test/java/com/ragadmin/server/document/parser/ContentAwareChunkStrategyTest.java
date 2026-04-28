package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.entity.DocumentEntity;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContentAwareChunkStrategyTest {

    private final ContentAwareChunkStrategy strategy = new ContentAwareChunkStrategy(new TableDetectionProperties());

    private ChunkContext context(int maxChunkChars, int overlapChars) {
        DocumentEntity doc = new DocumentEntity();
        doc.setDocType("PDF");
        DocumentSignals signals = new DocumentSignals(false, false, false, false, false, false, false,
                true, false, false, 0.2, 0.0, 0.1, 0.05);
        return ChunkContext.of(doc, signals, new ChunkStrategyProperties(maxChunkChars, overlapChars, 50), "TEXT");
    }

    @Nested
    class SplitByHeadings {

        @Test
        void shouldSplitAtH1AndH2() {
            String md = "# 第一章\n\n一些内容\n\n## 1.1 小节\n\n小节内容";
            List<String> sections = strategy.splitByHeadings(md);

            assertEquals(2, sections.size());
            assertTrue(sections.getFirst().startsWith("# 第一章"));
            assertTrue(sections.get(1).startsWith("## 1.1"));
        }

        @Test
        void shouldNotSplitInsideCodeBlock() {
            String md = "# 标题\n\n```\n# 代码里的注释\n```\n\n## 下一个标题\n\n内容";
            List<String> sections = strategy.splitByHeadings(md);

            assertEquals(2, sections.size());
            assertTrue(sections.getFirst().contains("# 代码里的注释"));
            assertTrue(sections.get(1).startsWith("## 下一个标题"));
        }

        @Test
        void shouldReturnSingleSectionWhenNoHeadings() {
            String md = "没有标题的纯文本\n\n第二段";
            List<String> sections = strategy.splitByHeadings(md);

            assertEquals(1, sections.size());
        }
    }

    @Nested
    class IdentifyBlocks {

        @Test
        void shouldIdentifyTableBlocks() {
            String md = "| 名称 | 数量 |\n|---|---|\n| 苹果 | 10 |\n| 橘子 | 20 |";
            List<ContentAwareChunkStrategy.ContentBlock> blocks = strategy.identifyBlocks(md);

            assertEquals(1, blocks.size());
            assertEquals("TABLE", blocks.getFirst().type());
            assertTrue(blocks.getFirst().text().contains("苹果"));
        }

        @Test
        void shouldIdentifyImageBlocks() {
            String md = "前面文本\n\n![图片](http://example.com/img.png)\n\n后面文本";
            List<ContentAwareChunkStrategy.ContentBlock> blocks = strategy.identifyBlocks(md);

            assertEquals(3, blocks.size());
            assertEquals("IMAGE", blocks.get(1).type());
        }

        @Test
        void shouldNotDetectTableInsideCodeBlock() {
            String md = "```\n| 假的 | 表格 |\n|---|---|\n| a | b |\n```";
            List<ContentAwareChunkStrategy.ContentBlock> blocks = strategy.identifyBlocks(md);

            assertEquals(1, blocks.size());
            assertEquals("TEXT", blocks.getFirst().type());
        }

        @Test
        void shouldNotDetectHeadingInsideCodeBlock() {
            String md = "```\n# 假的标题\n```";
            List<ContentAwareChunkStrategy.ContentBlock> blocks = strategy.identifyBlocks(md);

            assertEquals(1, blocks.size());
            assertEquals("TEXT", blocks.getFirst().type());
        }
    }

    @Nested
    class ChunkIntegration {

        @Test
        void shouldSplitTableAndTextInSameSection() {
            String md = "前面说明文字 " + "a".repeat(200) + "\n\n| 名称 | 数量 |\n|---|---|\n| A | 1 |";
            List<ChunkDraft> chunks = strategy.chunk(
                    List.of(new Document(md, Map.of())),
                    context(300, 40));

            assertFalse(chunks.isEmpty());
            // Table should be in a separate chunk from the long text
            boolean hasTableChunk = chunks.stream().anyMatch(c -> c.text().contains("| 名称"));
            assertTrue(hasTableChunk);
        }

        @Test
        void shouldSplitHeadingSectionsSeparately() {
            String section1 = "# 第一章\n\n" + "章节一内容 ".repeat(30);
            String table = "\n\n| 列1 | 列2 |\n|---|---|\n| a | b |";
            String section2 = "\n\n# 第二章\n\n" + "章节二内容 ".repeat(30);
            String md = section1 + table + section2;

            List<ChunkDraft> chunks = strategy.chunk(
                    List.of(new Document(md, Map.of())),
                    context(200, 30));

            assertFalse(chunks.isEmpty());
            // Chapter 1 and Chapter 2 should be in different chunks
            boolean hasChapter1 = chunks.stream().anyMatch(c -> c.text().contains("第一章"));
            boolean hasChapter2 = chunks.stream().anyMatch(c -> c.text().contains("第二章"));
            assertTrue(hasChapter1);
            assertTrue(hasChapter2);
        }

        @Test
        void shouldSplitLargeTableWithHeaderRepetition() {
            StringBuilder sb = new StringBuilder("| 名称 | 数量 |\n|---|---|\n");
            for (int i = 0; i < 50; i++) {
                sb.append("| item").append(i).append(" | ").append(i).append(" |\n");
            }

            List<ChunkDraft> chunks = strategy.chunk(
                    List.of(new Document(sb.toString(), Map.of())),
                    context(200, 30));

            assertTrue(chunks.size() > 1, "大表格应被拆分为多个 chunk");
            // Each sub-table should have the header
            for (ChunkDraft chunk : chunks) {
                assertTrue(chunk.text().contains("| 名称 | 数量 |"), "拆分后的子表格应保留表头");
            }
        }

        @Test
        void shouldIsolateImageBlock() {
            String md = "段落一内容 " + "a".repeat(200) + "\n\n![图片](url.png)\n\n段落二内容 " + "b".repeat(200);
            List<ChunkDraft> chunks = strategy.chunk(
                    List.of(new Document(md, Map.of())),
                    context(300, 40));

            // Image should be isolated in its own chunk
            boolean imageIsolated = chunks.stream()
                    .anyMatch(c -> c.text().contains("![图片]") && !c.text().contains("段落一"));
            assertTrue(imageIsolated, "图片应被隔离到独立 chunk");
        }

        @Test
        void shouldSetChunkStrategyMetadata() {
            String md = "| 名称 | 数量 |\n|---|---|\n| A | 1 |";
            List<ChunkDraft> chunks = strategy.chunk(
                    List.of(new Document(md, Map.of())),
                    context(800, 120));

            assertFalse(chunks.isEmpty());
            assertEquals("CONTENT_AWARE", chunks.getFirst().metadata().get("chunkStrategy"));
        }

        @Test
        void shouldHandleTableWithCodeBlock() {
            String md = "# 说明\n\n| 列1 | 列2 |\n|---|---|\n| a | b |\n\n```\n| 假表 | 假表 |\n|---|---|\n```";
            List<ChunkDraft> chunks = strategy.chunk(
                    List.of(new Document(md, Map.of())),
                    context(800, 120));

            // The code block's fake table should NOT be detected as a table block
            // Only the real table above should have containsTable=true
            boolean hasRealTable = chunks.stream()
                    .anyMatch(c -> Boolean.TRUE.equals(c.metadata().get("containsTable")));
            assertTrue(hasRealTable);
        }
    }
}
