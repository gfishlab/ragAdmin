package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(50)
public class MineruDocumentReaderStrategy implements DocumentReaderStrategy {

    private static final List<String> SUPPORTED_TYPES = List.of(
            "PDF",
            "PNG",
            "JPG",
            "JPEG",
            "WEBP"
    );

    private final MineruParseService mineruParseService;
    private final DocumentMetadataFactory documentMetadataFactory;

    public MineruDocumentReaderStrategy(MineruParseService mineruParseService, DocumentMetadataFactory documentMetadataFactory) {
        this.mineruParseService = mineruParseService;
        this.documentMetadataFactory = documentMetadataFactory;
    }

    @Override
    public boolean supports(DocumentParseRequest request) {
        return SUPPORTED_TYPES.contains(request.docType());
    }

    @Override
    public List<Document> read(DocumentParseRequest request) throws Exception {
        return documentMetadataFactory.enrichDocuments(
                mineruParseService.parseWithImages(request),
                request,
                "MINERU_API",
                "OCR"
        );
    }
}
