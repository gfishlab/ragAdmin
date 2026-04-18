package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.entity.DocumentVersionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MineruDocumentReaderStrategyTest {

    @Test
    void shouldUseMineruForImageDocument() throws Exception {
        MineruParseService mineruParseService = mock(MineruParseService.class);
        when(mineruParseService.parse(any())).thenReturn(List.of(new Document("MinerU 图片文本")));
        MineruDocumentReaderStrategy strategy = new MineruDocumentReaderStrategy(mineruParseService, new DocumentMetadataFactory());

        List<Document> documents = strategy.read(request("PNG"));

        assertEquals(1, documents.size());
        assertEquals("MinerU 图片文本", documents.getFirst().getText());
        assertEquals("MINERU_API", documents.getFirst().getMetadata().get("readerType"));
        assertEquals("OCR", documents.getFirst().getMetadata().get("parseMode"));
    }

    private DocumentParseRequest request(String docType) {
        DocumentEntity document = new DocumentEntity();
        document.setDocType(docType);
        document.setDocName("sample." + docType.toLowerCase());
        DocumentVersionEntity version = new DocumentVersionEntity();
        version.setStorageBucket("bucket");
        version.setStorageObjectKey("object");
        return new DocumentParseRequest(document, version, "fake".getBytes(), docType);
    }
}
