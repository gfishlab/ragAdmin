package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;

import java.util.List;

public interface DocumentSignalAnalyzer {

    DocumentSignals analyze(List<Document> documents, DocumentCleanContext context);
}
