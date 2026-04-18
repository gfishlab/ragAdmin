package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.entity.DocumentVersionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextDocumentReaderStrategyTest {

    private final TextDocumentReaderStrategy strategy = new TextDocumentReaderStrategy(new DocumentMetadataFactory());

    @Test
    void shouldReadTxtWithTextReader() throws Exception {
        List<Document> documents = strategy.read(request("TXT", "第一行\n第二行".getBytes()));

        assertEquals(1, documents.size());
        assertEquals("第一行\n第二行", documents.getFirst().getText());
        assertEquals("TEXT_READER", documents.getFirst().getMetadata().get("readerType"));
        assertEquals("TEXT", documents.getFirst().getMetadata().get("parseMode"));
        assertTrue(documents.getFirst().getMetadata().containsKey("charset"));
    }

    private DocumentParseRequest request(String docType, byte[] content) {
        DocumentEntity document = new DocumentEntity();
        document.setDocType(docType);
        document.setDocName("sample." + docType.toLowerCase());
        DocumentVersionEntity version = new DocumentVersionEntity();
        version.setStorageBucket("bucket");
        version.setStorageObjectKey("object");
        return new DocumentParseRequest(document, version, content, docType);
    }
}
