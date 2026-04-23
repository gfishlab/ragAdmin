package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;

import java.util.List;

public interface MineruParseService {

    List<Document> parse(DocumentParseRequest request) throws Exception;

    List<Document> parseByUrl(String presignedUrl, String fileName) throws Exception;

    OcrCapability describeCapability();
}
