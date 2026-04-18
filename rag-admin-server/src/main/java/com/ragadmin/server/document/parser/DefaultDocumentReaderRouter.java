package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DefaultDocumentReaderRouter implements DocumentReaderRouter {

    private final List<DocumentReaderStrategy> documentReaderStrategies;

    public DefaultDocumentReaderRouter(List<DocumentReaderStrategy> documentReaderStrategies) {
        this.documentReaderStrategies = documentReaderStrategies;
    }

    @Override
    public List<Document> read(DocumentParseRequest request) throws Exception {
        for (DocumentReaderStrategy documentReaderStrategy : documentReaderStrategies) {
            if (documentReaderStrategy.supports(request)) {
                return documentReaderStrategy.read(request);
            }
        }
        throw new IllegalArgumentException("当前文档类型暂未接入解析策略: " + request.docType());
    }
}
