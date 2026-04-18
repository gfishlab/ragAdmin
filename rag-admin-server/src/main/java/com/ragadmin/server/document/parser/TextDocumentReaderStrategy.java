package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(10)
public class TextDocumentReaderStrategy implements DocumentReaderStrategy {

    private static final List<String> SUPPORTED_TYPES = List.of("TXT", "TEX", "TEXT");

    private final DocumentMetadataFactory documentMetadataFactory;

    public TextDocumentReaderStrategy(DocumentMetadataFactory documentMetadataFactory) {
        this.documentMetadataFactory = documentMetadataFactory;
    }

    @Override
    public boolean supports(DocumentParseRequest request) {
        return SUPPORTED_TYPES.contains(request.docType());
    }

    @Override
    public List<Document> read(DocumentParseRequest request) {
        TextReader textReader = new TextReader(new ByteArrayResource(request.content(), request.document().getDocName()));
        return documentMetadataFactory.enrichDocuments(textReader.get(), request, "TEXT_READER", "TEXT");
    }
}
