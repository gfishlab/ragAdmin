package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.entity.DocumentVersionEntity;
import com.ragadmin.server.infra.storage.support.MinioClientFactory;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

@Component
public class DocumentContentExtractor {

    private final MinioClientFactory minioClientFactory;
    private final DocumentReaderRouter documentReaderRouter;

    public DocumentContentExtractor(MinioClientFactory minioClientFactory, DocumentReaderRouter documentReaderRouter) {
        this.minioClientFactory = minioClientFactory;
        this.documentReaderRouter = documentReaderRouter;
    }

    public List<Document> extract(DocumentEntity document, DocumentVersionEntity version) throws Exception {
        String docType = normalizeDocType(document.getDocType());
        byte[] content = loadContent(version);
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("文件内容为空，无法解析");
        }
        return documentReaderRouter.read(new DocumentParseRequest(document, version, content, docType));
    }

    protected byte[] loadContent(DocumentVersionEntity version) throws Exception {
        MinioClient minioClient = minioClientFactory.createClient();
        try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(version.getStorageBucket())
                .object(version.getStorageObjectKey())
                .build())) {
            return inputStream.readAllBytes();
        }
    }

    private String normalizeDocType(String docType) {
        if (!StringUtils.hasText(docType)) {
            return "";
        }
        return docType.trim().toUpperCase(Locale.ROOT);
    }
}
