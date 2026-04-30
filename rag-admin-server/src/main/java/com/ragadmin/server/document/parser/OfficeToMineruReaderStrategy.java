package com.ragadmin.server.document.parser;

import com.ragadmin.server.infra.storage.MinioProperties;
import com.ragadmin.server.infra.storage.support.MinioClientFactory;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

@Component
@Order(40)
public class OfficeToMineruReaderStrategy implements DocumentReaderStrategy {

    private static final Logger log = LoggerFactory.getLogger(OfficeToMineruReaderStrategy.class);
    private static final Set<String> SUPPORTED_TYPES = Set.of("DOCX", "PPTX");

    private final MineruParseService mineruParseService;
    private final MinioClientFactory minioClientFactory;
    private final MinioProperties minioProperties;
    private final DocumentMetadataFactory documentMetadataFactory;
    private final ImagePipelineProperties imagePipelineProperties;
    private final Tika tika = new Tika();

    public OfficeToMineruReaderStrategy(
            MineruParseService mineruParseService,
            MinioClientFactory minioClientFactory,
            MinioProperties minioProperties,
            DocumentMetadataFactory documentMetadataFactory,
            ImagePipelineProperties imagePipelineProperties) {
        this.mineruParseService = mineruParseService;
        this.minioClientFactory = minioClientFactory;
        this.minioProperties = minioProperties;
        this.documentMetadataFactory = documentMetadataFactory;
        this.imagePipelineProperties = imagePipelineProperties;
    }

    @Override
    public boolean supports(DocumentParseRequest request) {
        return SUPPORTED_TYPES.contains(request.docType());
    }

    @Override
    public List<Document> read(DocumentParseRequest request) throws Exception {
        String docType = request.docType();
        String fileName = request.document().getDocName();

        if (!mineruParseService.describeCapability().available()) {
            log.info("MinerU 不可用，降级到 Tika 解析，docType={}, fileName={}", docType, fileName);
            return readWithTika(request);
        }

        try {
            // 直接上传原始 DOCX/PPTX 到 MinIO，MinerU 原生支持 Office 格式
            String objectKey = request.version().getStorageObjectKey();
            String contentType = "DOCX".equals(docType)
                    ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    : "application/vnd.openxmlformats-officedocument.presentationml.presentation";

            minioClientFactory.createClient().putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectKey)
                            .stream(new ByteArrayInputStream(request.content()), request.content().length, -1)
                            .contentType(contentType)
                            .build()
            );

            String presignedUrl = minioClientFactory.createClient().getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioProperties.getBucketName())
                            .object(objectKey)
                            .expiry(imagePipelineProperties.getPresignedUrlExpirySeconds())
                            .build()
            );

            List<Document> documents = mineruParseService.parseByUrlWithImages(
                    presignedUrl, fileName,
                    minioProperties.getBucketName(), request.document().getKbId(), request.document().getId()
            );

            documents.forEach(doc -> doc.getMetadata().put("originalDocType", docType));

            return documentMetadataFactory.enrichDocuments(documents, request, "OFFICE_MINERU", "OCR");
        } catch (Exception e) {
            log.warn("Office 文档 MinerU 解析失败，降级到 Tika，docType={}, fileName={}, reason={}",
                    docType, fileName, e.getMessage());
            return readWithTika(request);
        }
    }

    private List<Document> readWithTika(DocumentParseRequest request) throws Exception {
        String parsed;
        try (InputStream tikaInputStream = new ByteArrayInputStream(request.content())) {
            parsed = tika.parseToString(tikaInputStream);
        }
        return documentMetadataFactory.enrichDocuments(
                List.of(new Document(parsed)),
                request,
                "TIKA",
                "TEXT"
        );
    }
}
