package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;

import java.util.List;

public interface MineruParseService {

    List<Document> parse(DocumentParseRequest request) throws Exception;

    List<Document> parseByUrl(String presignedUrl, String fileName) throws Exception;

    List<Document> parseWithImages(DocumentParseRequest request) throws Exception;

    List<Document> parseByUrlWithImages(String presignedUrl, String fileName,
                                        String bucket, Long kbId, Long documentId) throws Exception;

    OcrCapability describeCapability();
}
