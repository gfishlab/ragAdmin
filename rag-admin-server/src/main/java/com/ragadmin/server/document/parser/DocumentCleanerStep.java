package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;

import java.util.List;

public interface DocumentCleanerStep {

    boolean supports(DocumentCleanContext context);

    List<Document> clean(List<Document> documents, DocumentCleanContext context);
}
