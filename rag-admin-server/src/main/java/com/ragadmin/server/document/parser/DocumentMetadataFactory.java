package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DocumentMetadataFactory {

    public Map<String, Object> baseMetadata(DocumentParseRequest request, String readerType, String parseMode) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceFileName", request.document().getDocName());
        metadata.put("docType", request.docType());
        metadata.put("readerType", readerType);
        metadata.put("parseMode", parseMode);
        metadata.put("storageBucket", request.version().getStorageBucket());
        metadata.put("storageObjectKey", request.version().getStorageObjectKey());
        return metadata;
    }

    public List<Document> enrichDocuments(List<Document> documents, DocumentParseRequest request, String readerType, String parseMode) {
        List<Document> enrichedDocuments = new ArrayList<>();
        for (Document document : documents) {
            Map<String, Object> metadata = baseMetadata(request, readerType, parseMode);
            metadata.putAll(document.getMetadata());
            enrichedDocuments.add(new Document(document.getId(), document.getText(), metadata));
        }
        return enrichedDocuments;
    }
}
