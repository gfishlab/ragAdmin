package com.ragadmin.server.document.parser;

import org.apache.tika.Tika;
import org.springframework.ai.document.Document;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

@Component
@Order(100)
public class TikaDocumentReaderStrategy implements DocumentReaderStrategy {

    private final DocumentMetadataFactory documentMetadataFactory;
    private final Tika tika = new Tika();

    public TikaDocumentReaderStrategy(DocumentMetadataFactory documentMetadataFactory) {
        this.documentMetadataFactory = documentMetadataFactory;
    }

    @Override
    public boolean supports(DocumentParseRequest request) {
        return true;
    }

    @Override
    public List<Document> read(DocumentParseRequest request) throws Exception {
        String parsed = parseWithTika(request.content());
        return documentMetadataFactory.enrichDocuments(List.of(new Document(parsed)), request, "TIKA", "TEXT");
    }

    protected String parseWithTika(byte[] content) throws Exception {
        try (InputStream tikaInputStream = new ByteArrayInputStream(content)) {
            return tika.parseToString(tikaInputStream);
        }
    }
}
