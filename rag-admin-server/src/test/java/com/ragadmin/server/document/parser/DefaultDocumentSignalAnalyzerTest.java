package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.entity.DocumentEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultDocumentSignalAnalyzerTest {

    private DefaultDocumentSignalAnalyzer analyzer;
    private DocumentCleanContext context;

    @BeforeEach
    void setUp() {
        analyzer = new DefaultDocumentSignalAnalyzer();
        DocumentEntity doc = new DocumentEntity();
        doc.setDocType("PDF");
        context = new DocumentCleanContext(doc, DocumentCleanPolicy.defaultPolicy());
    }

    @Nested
    class RepeatedHeaderFooter {

        @Test
        void shouldDetectRepeatedHeader() {
            List<Document> docs = buildPages(10, "公司内部文件 请勿外传", null);
            DocumentSignals signals = analyzer.analyze(docs, context);
            assertTrue(signals.repeatedHeaderDetected());
        }

        @Test
        void shouldDetectRepeatedFooter() {
            List<Document> docs = buildPages(10, null, "第 1 页 / 共 10 页");
            DocumentSignals signals = analyzer.analyze(docs, context);
            assertTrue(signals.repeatedFooterDetected());
        }

        @Test
        void shouldNotDetectWhenTooFewPages() {
            List<Document> docs = buildPages(2, "页眉", "页脚");
            DocumentSignals signals = analyzer.analyze(docs, context);
            assertFalse(signals.repeatedHeaderDetected());
            assertFalse(signals.repeatedFooterDetected());
        }

        @Test
        void shouldDetectBrowserPrintHeaderInTwoPagePdf() {
            List<Document> docs = List.of(
                    doc("2026/4/12 12:42\n简历 | gfsh.online\n正文内容第一页"),
                    doc("2026/4/12 12:42\n简历 | gfsh.online\n正文内容第二页\n1/2")
            );
            DocumentSignals signals = analyzer.analyze(docs, context);
            assertTrue(signals.repeatedHeaderDetected());
            assertTrue(signals.repeatedFooterDetected());
        }

        @Test
        void shouldDetectPageNumberPatternAsFooter() {
            List<Document> docs = List.of(
                    doc("正文内容第一页\n1/3"),
                    doc("正文内容第二页\n2/3")
            );
            DocumentSignals signals = analyzer.analyze(docs, context);
            assertTrue(signals.repeatedFooterDetected());
        }

        @Test
        void shouldNotDetectWhenNoRepetition() {
            List<Document> docs = List.of(
                    doc("第一章 标题\n正文内容A"),
                    doc("第二章 标题\n正文内容B"),
                    doc("第三章 标题\n正文内容C")
            );
            DocumentSignals signals = analyzer.analyze(docs, context);
            assertFalse(signals.repeatedHeaderDetected());
            assertFalse(signals.repeatedFooterDetected());
        }
    }

    @Nested
    class TooManyBlankLines {

        @Test
        void shouldDetectExcessiveBlankLines() {
            String text = "段落1\n\n\n\n\n\n\n\n\n\n段落2\n\n\n\n\n\n\n\n\n\n";
            DocumentSignals signals = analyzer.analyze(List.of(doc(text)), context);
            assertTrue(signals.tooManyBlankLines());
        }

        @Test
        void shouldNotFlagNormalContent() {
            String text = "段落1\n\n段落2\n\n段落3";
            DocumentSignals signals = analyzer.analyze(List.of(doc(text)), context);
            assertFalse(signals.tooManyBlankLines());
        }
    }

    @Nested
    class WeakParagraphStructure {

        @Test
        void shouldDetectWeakStructure() {
            String text = "短行A\n\n短行B\n\n短行C\n\n短行D\n\n短行E";
            DocumentSignals signals = analyzer.analyze(List.of(doc(text)), context);
            assertTrue(signals.weakParagraphStructure());
        }

        @Test
        void shouldNotFlagWellStructuredContent() {
            String text = "第一段内容比较长，包含了多行文字\n第二行也属于第一段\n\n第二段内容也比较长\n这里也是第二段";
            DocumentSignals signals = analyzer.analyze(List.of(doc(text)), context);
            assertFalse(signals.weakParagraphStructure());
        }
    }

    @Nested
    class OcrNoise {

        @Test
        void shouldDetectOcrNoise() {
            String text = "正常文本 \uFFFD 噪声 \uFFFD 更多内容";
            DocumentSignals signals = analyzer.analyze(List.of(doc(text)), context);
            assertTrue(signals.ocrNoiseDetected());
        }

        @Test
        void shouldNotFlagCleanText() {
            String text = "干净的文本内容，没有噪声";
            DocumentSignals signals = analyzer.analyze(List.of(doc(text)), context);
            assertFalse(signals.ocrNoiseDetected());
        }
    }

    @Nested
    class SymbolDensity {

        @Test
        void shouldDetectHighSymbolDensity() {
            String text = "•••◆◆◆■■■●●●§§§¶¶¶★☆★☆•●◆■";
            DocumentSignals signals = analyzer.analyze(List.of(doc(text)), context);
            assertTrue(signals.symbolDensityHigh());
        }

        @Test
        void shouldNotFlagNormalSymbols() {
            String text = "正常文档内容，包含少量符号如-和。";
            DocumentSignals signals = analyzer.analyze(List.of(doc(text)), context);
            assertFalse(signals.symbolDensityHigh());
        }
    }

    @Nested
    class TocOutlineMissing {

        @Test
        void shouldDetectMissingTocWhenNoStructure() {
            String text = "这是一段普通文本\n没有章节结构\n也没有目录";
            DocumentSignals signals = analyzer.analyze(List.of(doc(text)), context);
            assertTrue(signals.tocOutlineMissing());
        }

        @Test
        void shouldNotFlagWhenChapterHeadingsPresent() {
            String text = "第一章 概述\n\n第一节 背景\n\n相关内容";
            DocumentSignals signals = analyzer.analyze(List.of(doc(text)), context);
            assertFalse(signals.tocOutlineMissing());
        }

        @Test
        void shouldNotFlagWhenTocPresent() {
            String text = "目录\n\n第一章 概述 ....... 1\n第二章 方法 ....... 15";
            DocumentSignals signals = analyzer.analyze(List.of(doc(text)), context);
            assertFalse(signals.tocOutlineMissing());
        }
    }

    @Test
    void shouldReturnEmptyForNullInput() {
        DocumentSignals signals = analyzer.analyze(null, context);
        assertEquals(DocumentSignals.empty(), signals);
    }

    @Test
    void shouldReturnEmptyForEmptyInput() {
        DocumentSignals signals = analyzer.analyze(List.of(), context);
        assertEquals(DocumentSignals.empty(), signals);
    }

    private Document doc(String text) {
        return new Document(text, Map.of());
    }

    private List<Document> buildPages(int count, String header, String footer) {
        java.util.List<Document> docs = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            StringBuilder sb = new StringBuilder();
            if (header != null) {
                sb.append(header).append("\n");
            }
            sb.append("第").append(i + 1).append("页正文内容");
            if (footer != null) {
                sb.append("\n").append(footer);
            }
            docs.add(doc(sb.toString()));
        }
        return docs;
    }
}
