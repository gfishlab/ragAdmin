package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(20)
public class MarkdownDocumentReaderStrategy implements DocumentReaderStrategy {

    private static final List<String> SUPPORTED_TYPES = List.of("MD", "MARKDOWN");

    private final DocumentMetadataFactory documentMetadataFactory;
    private final ImageReferenceResolver imageReferenceResolver;

    public MarkdownDocumentReaderStrategy(DocumentMetadataFactory documentMetadataFactory,
                                          ImageReferenceResolver imageReferenceResolver) {
        this.documentMetadataFactory = documentMetadataFactory;
        this.imageReferenceResolver = imageReferenceResolver;
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
        List<Document> documents = reader.get();
        documents = resolveImageReferences(documents, request);
        return documentMetadataFactory.enrichDocuments(documents, request, "MARKDOWN_READER", "TEXT");
    }

    private List<Document> resolveImageReferences(List<Document> documents, DocumentParseRequest request) {
        String bucket = request.document().getStorageBucket();
        Long kbId = request.document().getKbId();
        Long documentId = request.document().getId();
        if (bucket == null || kbId == null || documentId == null) {
            return documents;
        }
        List<Document> resolved = new ArrayList<>();
        for (Document doc : documents) {
            ImageResolutionResult result = imageReferenceResolver.resolveImages(doc.getText(), bucket, kbId, documentId);
            Map<String, Object> metadata = new LinkedHashMap<>(doc.getMetadata());
            metadata.put("imageReport", result.report());
            resolved.add(new Document(doc.getId(), result.markdown(), metadata));
        }
        return resolved;
    }
}
