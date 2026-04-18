package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.entity.DocumentEntity;
import org.springframework.ai.document.Document;

import java.util.List;

public record DocumentCleaningRequest(
        DocumentEntity document,
        List<Document> documents
) {
}
