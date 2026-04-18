package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.jsoup.JsoupDocumentReader;
import org.springframework.ai.reader.jsoup.config.JsoupDocumentReaderConfig;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(30)
public class HtmlDocumentReaderStrategy implements DocumentReaderStrategy {

    private static final List<String> SUPPORTED_TYPES = List.of("HTML", "HTM");

    private final DocumentMetadataFactory documentMetadataFactory;

    public HtmlDocumentReaderStrategy(DocumentMetadataFactory documentMetadataFactory) {
        this.documentMetadataFactory = documentMetadataFactory;
    }

    @Override
    public boolean supports(DocumentParseRequest request) {
        return SUPPORTED_TYPES.contains(request.docType());
    }

    @Override
    public List<Document> read(DocumentParseRequest request) {
        JsoupDocumentReader reader = new JsoupDocumentReader(
                new ByteArrayResource(request.content(), request.document().getDocName()),
                JsoupDocumentReaderConfig.defaultConfig()
        );
        return documentMetadataFactory.enrichDocuments(reader.get(), request, "HTML_READER", "TEXT");
    }
}
