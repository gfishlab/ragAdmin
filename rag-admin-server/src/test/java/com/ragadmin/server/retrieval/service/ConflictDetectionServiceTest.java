package com.ragadmin.server.retrieval.service;

import com.ragadmin.server.document.entity.ChunkEntity;
import com.ragadmin.server.model.service.ModelService;
import com.ragadmin.server.retrieval.config.ConflictDetectionProperties;
import com.ragadmin.server.retrieval.model.ConflictDetectionResult;
import com.ragadmin.server.retrieval.model.ConflictType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConflictDetectionServiceTest {

    private ConflictDetectionService service;
    private ConflictDetectionProperties properties;
    private ModelService modelService;

    @BeforeEach
    void setUp() throws Exception {
        service = new ConflictDetectionService();
        properties = new ConflictDetectionProperties();
        properties.setEnabled(true);
        properties.setMethod("LLM_NLI");

        modelService = mock(ModelService.class);
        when(modelService.findDefaultChatModelDescriptor()).thenReturn(null);

        injectField("properties", properties);
        injectField("objectMapper", new com.fasterxml.jackson.databind.ObjectMapper());
        injectField("modelService", modelService);
    }

    private void injectField(String name, Object value) throws Exception {
        Field field = ConflictDetectionService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    private ChunkEntity makeChunk(Long id, Long documentId, Long versionId, String text, LocalDateTime createdAt) {
        ChunkEntity chunk = new ChunkEntity();
        chunk.setId(id);
        chunk.setKbId(1L);
        chunk.setDocumentId(documentId);
        chunk.setDocumentVersionId(versionId);
        chunk.setChunkNo(1);
        chunk.setChunkText(text);
        chunk.setTokenCount(text != null ? text.length() / 4 : 0);
        chunk.setCharCount(text != null ? text.length() : 0);
        chunk.setEnabled(true);
        chunk.setCreatedAt(createdAt);
        return chunk;
    }

    private RetrievalService.RetrievedChunk chunk(Long id, Long docId, Long versionId, String text) {
        return new RetrievalService.RetrievedChunk(
                makeChunk(id, docId, versionId, text, LocalDateTime.now()), 0.9);
    }

    private RetrievalService.RetrievedChunk chunk(Long id, Long docId, Long versionId, String text, double score) {
        return new RetrievalService.RetrievedChunk(
                makeChunk(id, docId, versionId, text, LocalDateTime.now()), score);
    }

    @Test
    void shouldSkipWhenDisabled() {
        properties.setEnabled(false);
        var c1 = chunk(1L, 100L, 1L, "文本A");
        var c2 = chunk(2L, 100L, 2L, "文本B");

        ConflictDetectionResult result = service.detect("测试", List.of(c1, c2));

        assertFalse(result.hasConflicts());
        assertEquals(2, result.getResolvedChunks().size());
    }

    @Test
    void shouldSkipWhenSingleChunk() {
        var c1 = chunk(1L, 100L, 1L, "文本A");

        ConflictDetectionResult result = service.detect("测试", List.of(c1));

        assertFalse(result.hasConflicts());
        assertEquals(1, result.getResolvedChunks().size());
    }

    @Test
    void shouldDetectVersionConflict() {
        var c1 = chunk(1L, 100L, 1L, "2023 版不支持该功能", 0.8);
        var c2 = chunk(2L, 100L, 2L, "2024 版支持该功能", 0.9);

        ConflictDetectionResult result = service.detect("测试", List.of(c1, c2));

        assertTrue(result.hasConflicts());
        assertEquals(1, result.getConflicts().size());
        assertEquals(ConflictType.VERSION, result.getConflicts().get(0).getConflictType());
        assertEquals(2L, result.getConflicts().get(0).getPreferredChunkId());
    }

    @Test
    void shouldNotConflictAcrossDocuments() {
        var c1 = chunk(1L, 100L, 1L, "文档A的内容");
        var c2 = chunk(2L, 200L, 1L, "文档B的内容");

        ConflictDetectionResult result = service.detect("测试", List.of(c1, c2));

        // 不同文档不同版本不构成版本冲突
        assertFalse(result.hasConflicts());
    }

    @Test
    void shouldResolveByVersion() {
        properties.getResolution().setEnabled(true);
        var c1 = chunk(1L, 100L, 1L, "旧版内容", 0.8);
        var c2 = chunk(2L, 100L, 2L, "新版内容", 0.9);

        ConflictDetectionResult result = service.detect("测试", List.of(c1, c2));

        // 版本冲突消解后，应保留高版本 chunk
        assertEquals(1, result.getResolvedChunks().size());
        assertEquals(2L, result.getResolvedChunks().get(0).chunk().getId());
    }

    @Test
    void shouldResolveByTimestamp() {
        properties.getResolution().setEnabled(true);
        LocalDateTime older = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime newer = LocalDateTime.of(2025, 1, 1, 0, 0);

        ChunkEntity chunkOld = makeChunk(1L, 100L, 1L, "旧内容", older);
        ChunkEntity chunkNew = makeChunk(2L, 100L, 1L, "新内容", newer);

        var c1 = new RetrievalService.RetrievedChunk(chunkOld, 0.8);
        var c2 = new RetrievalService.RetrievedChunk(chunkNew, 0.9);

        // 同版本号但不同时间戳，不应该触发版本冲突
        ConflictDetectionResult result = service.detect("测试", List.of(c1, c2));

        // 同版本号不算版本冲突
        assertFalse(result.hasConflicts());
        assertEquals(2, result.getResolvedChunks().size());
    }

    @Test
    void shouldKeepAllWhenResolutionDisabled() {
        properties.getResolution().setEnabled(false);
        var c1 = chunk(1L, 100L, 1L, "旧版内容", 0.8);
        var c2 = chunk(2L, 100L, 2L, "新版内容", 0.9);

        ConflictDetectionResult result = service.detect("测试", List.of(c1, c2));

        // 不消解，都保留但打上冲突标记
        assertTrue(result.hasConflicts());
        assertEquals(2, result.getResolvedChunks().size());
    }

    @Test
    void shouldReturnEmptyOnEmptyInput() {
        ConflictDetectionResult result = service.detect("测试", List.of());
        assertFalse(result.hasConflicts());
        assertTrue(result.getResolvedChunks().isEmpty());
    }
}
