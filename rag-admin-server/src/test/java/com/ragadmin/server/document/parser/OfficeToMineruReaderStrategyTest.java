package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.entity.DocumentVersionEntity;
import com.ragadmin.server.infra.storage.MinioProperties;
import com.ragadmin.server.infra.storage.support.MinioClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OfficeToMineruReaderStrategyTest {

    @Test
    void shouldSupportDocxAndPptx() {
        OfficeToMineruReaderStrategy strategy = createStrategy(false);

        assertTrue(strategy.supports(request("DOCX")));
        assertTrue(strategy.supports(request("PPTX")));
        assertFalse(strategy.supports(request("PDF")));
        assertFalse(strategy.supports(request("XLSX")));
    }

    @Test
    void shouldFallbackToTikaWhenMineruUnavailable() throws Exception {
        MineruParseService mineruParseService = mock(MineruParseService.class);
        when(mineruParseService.describeCapability())
                .thenReturn(new OcrCapability(true, false, "MinerU 不可用", "ch", 600));

        OfficeToMineruReaderStrategy strategy = new OfficeToMineruReaderStrategy(
                mineruParseService,
                mock(MinioClientFactory.class),
                mock(MinioProperties.class),
                new DocumentMetadataFactory(),
                new ImagePipelineProperties()
        );

        List<Document> documents = strategy.read(request("DOCX"));

        assertEquals(1, documents.size());
        assertEquals("TIKA", documents.getFirst().getMetadata().get("readerType"));
    }

    @Test
    void shouldCallMineruWhenAvailable() throws Exception {
        MineruParseService mineruParseService = mock(MineruParseService.class);
        when(mineruParseService.describeCapability())
                .thenReturn(new OcrCapability(true, true, "MinerU 可用", "ch", 600));
        when(mineruParseService.parseByUrlWithImages(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(List.of(new Document("MinerU 解析结果")));

        MinioClientFactory minioClientFactory = mock(MinioClientFactory.class);
        io.minio.MinioClient minioClient = mock(io.minio.MinioClient.class);
        when(minioClientFactory.createClient()).thenReturn(minioClient);
        when(minioClient.getPresignedObjectUrl(any(io.minio.GetPresignedObjectUrlArgs.class)))
                .thenReturn("https://minio.example.com/document.docx");

        MinioProperties minioProperties = mock(MinioProperties.class);
        when(minioProperties.getBucketName()).thenReturn("test-bucket");

        OfficeToMineruReaderStrategy strategy = new OfficeToMineruReaderStrategy(
                mineruParseService,
                minioClientFactory,
                minioProperties,
                new DocumentMetadataFactory(),
                new ImagePipelineProperties()
        );

        List<Document> documents = strategy.read(request("DOCX"));

        assertEquals(1, documents.size());
        assertEquals("OFFICE_MINERU", documents.getFirst().getMetadata().get("readerType"));
        assertEquals("OCR", documents.getFirst().getMetadata().get("parseMode"));
        assertEquals("DOCX", documents.getFirst().getMetadata().get("originalDocType"));
        verify(mineruParseService).parseByUrlWithImages("https://minio.example.com/document.docx", "测试文档.docx", "test-bucket", null, null);
    }

    @Test
    void shouldFallbackToTikaWhenMineruParseFails() throws Exception {
        MineruParseService mineruParseService = mock(MineruParseService.class);
        when(mineruParseService.describeCapability())
                .thenReturn(new OcrCapability(true, true, "MinerU 可用", "ch", 600));
        when(mineruParseService.parseByUrlWithImages(anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(new RuntimeException("MinerU 解析异常"));

        MinioClientFactory minioClientFactory = mock(MinioClientFactory.class);
        io.minio.MinioClient minioClient = mock(io.minio.MinioClient.class);
        when(minioClientFactory.createClient()).thenReturn(minioClient);
        when(minioClient.getPresignedObjectUrl(any(io.minio.GetPresignedObjectUrlArgs.class)))
                .thenReturn("https://minio.example.com/document.docx");
        when(minioClient.putObject(any())).thenReturn(null);

        MinioProperties minioProperties = mock(MinioProperties.class);
        when(minioProperties.getBucketName()).thenReturn("test-bucket");

        OfficeToMineruReaderStrategy strategy = new OfficeToMineruReaderStrategy(
                mineruParseService,
                minioClientFactory,
                minioProperties,
                new DocumentMetadataFactory(),
                new ImagePipelineProperties()
        );

        List<Document> documents = strategy.read(request("DOCX"));

        assertEquals(1, documents.size());
        assertEquals("TIKA", documents.getFirst().getMetadata().get("readerType"));
    }

    private OfficeToMineruReaderStrategy createStrategy(boolean mineruAvailable) {
        MineruParseService mineruParseService = mock(MineruParseService.class);
        when(mineruParseService.describeCapability())
                .thenReturn(new OcrCapability(true, mineruAvailable, "test", "ch", 600));

        return new OfficeToMineruReaderStrategy(
                mineruParseService,
                mock(MinioClientFactory.class),
                mock(MinioProperties.class),
                new DocumentMetadataFactory(),
                new ImagePipelineProperties()
        );
    }

    private DocumentParseRequest request(String docType) {
        DocumentEntity document = new DocumentEntity();
        document.setDocType(docType);
        document.setDocName("测试文档." + docType.toLowerCase());
        DocumentVersionEntity version = new DocumentVersionEntity();
        version.setStorageBucket("bucket");
        version.setStorageObjectKey("object-key");
        return new DocumentParseRequest(document, version, "fake-content".getBytes(), docType);
    }
}
