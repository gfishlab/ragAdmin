package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(20)
public class MarkdownDocumentReaderStrategy implements DocumentReaderStrategy {

    private static final List<String> SUPPORTED_TYPES = List.of("MD", "MARKDOWN");

    private final DocumentMetadataFactory documentMetadataFactory;

    public MarkdownDocumentReaderStrategy(DocumentMetadataFactory documentMetadataFactory) {
        this.documentMetadataFactory = documentMetadataFactory;
    }

    @Override
    public boolean supports(DocumentParseRequest request) {
        return SUPPORTED_TYPES.contains(request.docType());
    }

    @Override
    public List<Document> read(DocumentParseRequest request) {
        MarkdownDocumentReader reader = new MarkdownDocumentReader(
                new ByteArrayResource(request.content(), request.document().getDocName()),
                MarkdownDocumentReaderConfig.defaultConfig()
        );
        return documentMetadataFactory.enrichDocuments(reader.get(), request, "MARKDOWN_READER", "TEXT");
    }
}
