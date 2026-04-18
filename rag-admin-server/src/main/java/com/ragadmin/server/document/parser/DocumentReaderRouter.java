package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;

import java.util.List;

public interface DocumentReaderRouter {

    List<Document> read(DocumentParseRequest request) throws Exception;
}
