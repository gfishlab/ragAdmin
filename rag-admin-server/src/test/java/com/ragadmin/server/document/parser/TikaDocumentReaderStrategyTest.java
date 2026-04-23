package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.entity.DocumentVersionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TikaDocumentReaderStrategyTest {

    @Test
    void shouldSupportAnyDocType() {
        TikaDocumentReaderStrategy strategy = new TikaDocumentReaderStrategy(new DocumentMetadataFactory());
        assertEquals(true, strategy.supports(request("RTF")));
        assertEquals(true, strategy.supports(request("EPUB")));
        assertEquals(true, strategy.supports(request("DOCX")));
        assertEquals(true, strategy.supports(request("UNKNOWN")));
    }

    @Test
    void shouldParseWithTika() throws Exception {
        TikaDocumentReaderStrategy strategy = new TikaDocumentReaderStrategy(new DocumentMetadataFactory()) {
            @Override
            protected String parseWithTika(byte[] content) {
                return "Tika 提取文本";
            }
        };

        List<Document> documents = strategy.read(request("RTF"));

        assertEquals(1, documents.size());
        assertEquals("Tika 提取文本", documents.getFirst().getText());
        assertEquals("TEXT", documents.getFirst().getMetadata().get("parseMode"));
        assertEquals("TIKA", documents.getFirst().getMetadata().get("readerType"));
    }

    private DocumentParseRequest request(String docType) {
        return request(docType, "fake".getBytes());
    }

    private DocumentParseRequest request(String docType, byte[] content) {
        DocumentEntity document = new DocumentEntity();
        document.setDocType(docType);
        document.setDocName("测试文档." + docType.toLowerCase());
        DocumentVersionEntity version = new DocumentVersionEntity();
        version.setStorageBucket("bucket");
        version.setStorageObjectKey("object");
        return new DocumentParseRequest(document, version, content, docType);
    }
}
