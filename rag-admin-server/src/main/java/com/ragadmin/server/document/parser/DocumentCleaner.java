package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;

import java.util.List;

public interface DocumentCleaner {

    List<Document> clean(List<Document> documents, DocumentCleanContext context);
}
