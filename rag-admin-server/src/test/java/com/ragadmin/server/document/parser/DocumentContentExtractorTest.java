package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.entity.DocumentVersionEntity;
import com.ragadmin.server.infra.storage.support.MinioClientFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.document.Document;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentContentExtractorTest {

    @Mock
    private MinioClientFactory minioClientFactory;

    @Mock
    private DocumentReaderRouter documentReaderRouter;

    @Test
    void shouldDelegateToReaderRouter() throws Exception {
        DocumentContentExtractor extractor = new TestableDocumentContentExtractor(minioClientFactory, documentReaderRouter, "fake-image".getBytes());
        when(documentReaderRouter.read(any())).thenReturn(List.of(new Document("图片 OCR 文本")));

        List<Document> documents = extractor.extract(document("PNG"), version());

        assertEquals(1, documents.size());
        assertEquals("图片 OCR 文本", documents.getFirst().getText());
        verify(documentReaderRouter).read(argThat(request ->
                "PNG".equals(request.docType())
                        && "测试文档.PNG".equals(request.document().getDocName())
                        && request.content().length > 0
        ));
    }

    @Test
    void shouldRejectUnsupportedDocTypeBeforeRouting() throws Exception {
        DocumentContentExtractor extractor = new TestableDocumentContentExtractor(minioClientFactory, documentReaderRouter, "fake-binary".getBytes());
        when(documentReaderRouter.read(any())).thenThrow(new IllegalArgumentException("当前文档类型暂未接入解析策略: BIN"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> extractor.extract(document("BIN"), version())
        );

        assertEquals("当前文档类型暂未接入解析策略: BIN", exception.getMessage());
    }

    @Test
    void shouldRejectZeroByteDocumentBeforeInvokingTika() {
        DocumentContentExtractor extractor = new TestableDocumentContentExtractor(minioClientFactory, documentReaderRouter, new byte[0]);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> extractor.extract(document("PDF"), version())
        );

        assertEquals("文件内容为空，无法解析", exception.getMessage());
    }

    private DocumentEntity document(String docType) {
        DocumentEntity entity = new DocumentEntity();
        entity.setDocType(docType);
        entity.setDocName("测试文档." + docType);
        entity.setStorageBucket("bucket");
        entity.setStorageObjectKey("folder/object." + docType.toLowerCase());
        return entity;
    }

    private DocumentVersionEntity version() {
        DocumentVersionEntity entity = new DocumentVersionEntity();
        entity.setStorageBucket("bucket");
        entity.setStorageObjectKey("object");
        return entity;
    }

    private static class TestableDocumentContentExtractor extends DocumentContentExtractor {

        private final byte[] content;

        private TestableDocumentContentExtractor(MinioClientFactory minioClientFactory, DocumentReaderRouter documentReaderRouter, byte[] content) {
            super(minioClientFactory, documentReaderRouter);
            this.content = content;
        }

        @Override
        protected byte[] loadContent(DocumentVersionEntity version) {
            return content;
        }
    }
}
