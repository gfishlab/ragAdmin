package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.mapper.ChunkMapper;
import com.ragadmin.server.document.mapper.DocumentMapper;
import com.ragadmin.server.document.mapper.DocumentParseTaskMapper;
import com.ragadmin.server.document.mapper.DocumentVersionMapper;
import com.ragadmin.server.document.support.ChunkVectorizationService;
import com.ragadmin.server.document.support.DocumentVectorizationProperties;
import com.ragadmin.server.document.support.DocumentVectorizationStrategyResolver;
import com.ragadmin.server.knowledge.service.KnowledgeBaseService;
import com.ragadmin.server.task.mapper.TaskStepRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.document.Document;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DocumentParseProcessorTest {

    @Mock
    private DocumentParseTaskMapper documentParseTaskMapper;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private DocumentVersionMapper documentVersionMapper;

    @Mock
    private ChunkMapper chunkMapper;

    @Mock
    private DocumentContentExtractor documentContentExtractor;

    @Mock
    private DefaultDocumentCleaner documentCleaner;

    @Mock
    private TaskStepRecordMapper taskStepRecordMapper;

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private ChunkVectorizationService chunkVectorizationService;

    @Mock
    private DocumentVectorizationStrategyResolver strategyResolver;

    @Spy
    @InjectMocks
    private DocumentParseProcessor documentParseProcessor;

    @Test
    void shouldSkipTaskWhenTaskStatusChanged() {
        doThrow(new IllegalStateException("任务不存在或状态已变更"))
                .when(documentParseProcessor)
                .markRunning(13L);

        documentParseProcessor.processSingleTask(13L);

        verify(documentParseProcessor, never()).markFailed(eq(13L), any());
        verify(documentParseTaskMapper, never()).selectById(13L);
    }

    @Test
    void shouldSplitDocumentsByProviderStrategyAndKeepMetadata() {
        String content = """
                %s

                %s

                %s
                """.formatted("a".repeat(220), "b".repeat(220), "c".repeat(220));

        List<DocumentParseProcessor.ChunkDraft> chunks = documentParseProcessor.splitIntoChunks(
                List.of(new Document(content, Map.of("pageNo", 3, "readerType", "TIKA", "parseMode", "TEXT"))),
                new DocumentVectorizationProperties.StrategyProperties(1, 400, 80)
        );

        assertEquals(3, chunks.size());
        assertEquals(220, chunks.getFirst().text().length());
        assertEquals(302, chunks.get(1).text().length());
        assertEquals(302, chunks.get(2).text().length());
        assertEquals(3, chunks.getFirst().metadata().get("pageNo"));
        assertEquals("TIKA", chunks.getFirst().metadata().get("readerType"));
        assertEquals(0, chunks.getFirst().metadata().get("chunkIndex"));
        assertEquals(3, chunks.getFirst().metadata().get("totalChunks"));
    }
}
