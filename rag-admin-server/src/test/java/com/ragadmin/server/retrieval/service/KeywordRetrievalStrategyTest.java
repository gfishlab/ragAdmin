package com.ragadmin.server.retrieval.service;

import com.ragadmin.server.document.entity.ChunkEntity;
import com.ragadmin.server.document.mapper.ChunkMapper;
import com.ragadmin.server.infra.elasticsearch.ElasticsearchClient;
import com.ragadmin.server.infra.elasticsearch.ElasticsearchProperties;
import com.ragadmin.server.knowledge.entity.KnowledgeBaseEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KeywordRetrievalStrategyTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private ElasticsearchProperties elasticsearchProperties;

    @Mock
    private ChunkMapper chunkMapper;

    private KeywordRetrievalStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new KeywordRetrievalStrategy();
        ReflectionTestUtils.setField(strategy, "elasticsearchClient", elasticsearchClient);
        ReflectionTestUtils.setField(strategy, "elasticsearchProperties", elasticsearchProperties);
        ReflectionTestUtils.setField(strategy, "chunkMapper", chunkMapper);
    }

    @Test
    void shouldReturnScoredChunksFromES() {
        when(elasticsearchProperties.isEnabled()).thenReturn(true);

        ElasticsearchClient.ScoredDocument doc1 = new ElasticsearchClient.ScoredDocument(100L, 8.5, Map.of("chunk_text", "测试文本"));
        ElasticsearchClient.ScoredDocument doc2 = new ElasticsearchClient.ScoredDocument(101L, 6.2, Map.of("chunk_text", "其他文本"));
        when(elasticsearchClient.searchWithScores("kb_1_chunks", "测试查询", 5))
                .thenReturn(List.of(doc1, doc2));

        ChunkEntity chunk1 = new ChunkEntity();
        chunk1.setId(100L);
        chunk1.setChunkText("测试文本");
        chunk1.setKbId(1L);
        chunk1.setDocumentId(10L);
        chunk1.setChunkNo(1);
        ChunkEntity chunk2 = new ChunkEntity();
        chunk2.setId(101L);
        chunk2.setChunkText("其他文本");
        chunk2.setKbId(1L);
        chunk2.setDocumentId(10L);
        chunk2.setChunkNo(2);
        when(chunkMapper.selectBatchIds(List.of(100L, 101L))).thenReturn(List.of(chunk1, chunk2));

        KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
        kb.setId(1L);

        List<RetrievalService.RetrievedChunk> results = strategy.retrieve(kb, "测试查询", 5);

        assertEquals(2, results.size());
        assertEquals(100L, results.getFirst().chunk().getId());
        assertEquals(8.5, results.getFirst().score(), 0.001);
        assertTrue(results.getFirst().score() >= results.get(1).score());
    }

    @Test
    void shouldReturnEmptyWhenESDisabled() {
        when(elasticsearchProperties.isEnabled()).thenReturn(false);

        KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
        kb.setId(1L);

        List<RetrievalService.RetrievedChunk> results = strategy.retrieve(kb, "测试查询", 5);

        assertTrue(results.isEmpty());
        verifyNoInteractions(elasticsearchClient);
    }

    @Test
    void shouldDeduplicateByChunkId() {
        when(elasticsearchProperties.isEnabled()).thenReturn(true);

        ElasticsearchClient.ScoredDocument doc1 = new ElasticsearchClient.ScoredDocument(100L, 8.5, Map.of());
        ElasticsearchClient.ScoredDocument doc2 = new ElasticsearchClient.ScoredDocument(100L, 6.2, Map.of());
        when(elasticsearchClient.searchWithScores("kb_1_chunks", "查询", 5))
                .thenReturn(List.of(doc1, doc2));

        ChunkEntity chunk1 = new ChunkEntity();
        chunk1.setId(100L);
        chunk1.setChunkText("文本");
        chunk1.setChunkNo(1);
        when(chunkMapper.selectBatchIds(List.of(100L))).thenReturn(List.of(chunk1));

        KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
        kb.setId(1L);

        List<RetrievalService.RetrievedChunk> results = strategy.retrieve(kb, "查询", 5);

        assertEquals(1, results.size());
        assertEquals(8.5, results.getFirst().score(), 0.001);
    }

    @Test
    void shouldReturnEmptyWhenNoResults() {
        when(elasticsearchProperties.isEnabled()).thenReturn(true);
        when(elasticsearchClient.searchWithScores("kb_1_chunks", "查询", 5))
                .thenReturn(List.of());

        KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
        kb.setId(1L);

        List<RetrievalService.RetrievedChunk> results = strategy.retrieve(kb, "查询", 5);

        assertTrue(results.isEmpty());
    }
}
