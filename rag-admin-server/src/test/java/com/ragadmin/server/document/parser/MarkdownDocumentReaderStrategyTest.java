package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.entity.DocumentVersionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownDocumentReaderStrategyTest {

    private final MarkdownDocumentReaderStrategy strategy = new MarkdownDocumentReaderStrategy(new DocumentMetadataFactory());

    @Test
    void shouldReadMarkdownAndPreserveHeaderMetadata() throws Exception {
        List<Document> documents = strategy.read(request("# 第一章\n\n正文内容".getBytes()));

        assertEquals(1, documents.size());
        assertEquals("正文内容", documents.getFirst().getText());
        assertEquals("MARKDOWN_READER", documents.getFirst().getMetadata().get("readerType"));
        assertEquals("TEXT", documents.getFirst().getMetadata().get("parseMode"));
        assertEquals("第一章", documents.getFirst().getMetadata().get("title"));
        assertTrue(documents.getFirst().getMetadata().containsKey("category"));
    }

    private DocumentParseRequest request(byte[] content) {
        DocumentEntity document = new DocumentEntity();
        document.setDocType("MD");
        document.setDocName("sample.md");
        DocumentVersionEntity version = new DocumentVersionEntity();
        version.setStorageBucket("bucket");
        version.setStorageObjectKey("object");
        return new DocumentParseRequest(document, version, content, "MD");
    }
}
