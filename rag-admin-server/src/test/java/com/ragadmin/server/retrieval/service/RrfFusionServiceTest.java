package com.ragadmin.server.retrieval.service;

import com.ragadmin.server.document.entity.ChunkEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RrfFusionServiceTest {

    private RrfFusionService rrfFusionService;

    @BeforeEach
    void setUp() {
        rrfFusionService = new RrfFusionService();
    }

    @Test
    void shouldCalculateRrfScoreCorrectly() {
        assertEquals(1.0 / 61.0, rrfFusionService.rrfScore(1, 60), 0.0001);
        assertEquals(1.0 / 62.0, rrfFusionService.rrfScore(2, 60), 0.0001);
        assertEquals(1.0 / 160.0, rrfFusionService.rrfScore(100, 60), 0.0001);
    }

    @Test
    void shouldFuseTwoListsCorrectly() {
        ChunkEntity chunk1 = chunk(1L, "语义结果1");
        ChunkEntity chunk2 = chunk(2L, "语义结果2");
        ChunkEntity chunk3 = chunk(3L, "关键词结果1");
        ChunkEntity chunk4 = chunk(4L, "关键词结果2");

        List<RetrievalService.RetrievedChunk> semantic = List.of(
                new RetrievalService.RetrievedChunk(chunk1, 0.95),
                new RetrievalService.RetrievedChunk(chunk2, 0.85)
        );
        List<RetrievalService.RetrievedChunk> keyword = List.of(
                new RetrievalService.RetrievedChunk(chunk3, 10.5),
                new RetrievalService.RetrievedChunk(chunk4, 8.2)
        );

        List<RetrievalService.RetrievedChunk> fused = rrfFusionService.fuse(semantic, keyword, 60, 10);

        assertEquals(4, fused.size());
        // chunk1 和 chunk3 各占一路 rank1，所以它们的融合分数应最高
        double scoreChunk1 = 1.0 / 61.0;
        double scoreChunk3 = 1.0 / 61.0;
        assertEquals(scoreChunk1, fused.getFirst().score(), 0.0001);
    }

    @Test
    void shouldPrioritizeChunksInBothLists() {
        // chunk1 同时出现在两路结果中，应获得最高融合分数
        ChunkEntity chunk1 = chunk(1L, "双命中");
        ChunkEntity chunk2 = chunk(2L, "仅语义");
        ChunkEntity chunk3 = chunk(3L, "仅关键词");

        List<RetrievalService.RetrievedChunk> semantic = List.of(
                new RetrievalService.RetrievedChunk(chunk1, 0.95),
                new RetrievalService.RetrievedChunk(chunk2, 0.80)
        );
        List<RetrievalService.RetrievedChunk> keyword = List.of(
                new RetrievalService.RetrievedChunk(chunk1, 12.0),
                new RetrievalService.RetrievedChunk(chunk3, 8.5)
        );

        List<RetrievalService.RetrievedChunk> fused = rrfFusionService.fuse(semantic, keyword, 60, 10);

        // chunk1 在两路中都是 rank1，融合分 = 2 * (1/61)
        double expectedTopScore = 2.0 / 61.0;
        assertEquals(3, fused.size());
        assertEquals(1L, fused.getFirst().chunk().getId());
        assertEquals(expectedTopScore, fused.getFirst().score(), 0.0001);
    }

    @Test
    void shouldHandleEmptySemanticList() {
        ChunkEntity chunk1 = chunk(1L, "关键词结果");

        List<RetrievalService.RetrievedChunk> semantic = List.of();
        List<RetrievalService.RetrievedChunk> keyword = List.of(
                new RetrievalService.RetrievedChunk(chunk1, 10.0)
        );

        List<RetrievalService.RetrievedChunk> fused = rrfFusionService.fuse(semantic, keyword, 60, 10);

        assertEquals(1, fused.size());
        assertEquals(1L, fused.getFirst().chunk().getId());
    }

    @Test
    void shouldHandleEmptyKeywordList() {
        ChunkEntity chunk1 = chunk(1L, "语义结果");

        List<RetrievalService.RetrievedChunk> semantic = List.of(
                new RetrievalService.RetrievedChunk(chunk1, 0.95)
        );
        List<RetrievalService.RetrievedChunk> keyword = List.of();

        List<RetrievalService.RetrievedChunk> fused = rrfFusionService.fuse(semantic, keyword, 60, 10);

        assertEquals(1, fused.size());
        assertEquals(1L, fused.getFirst().chunk().getId());
    }

    @Test
    void shouldRespectMaxResults() {
        List<RetrievalService.RetrievedChunk> semantic = List.of(
                new RetrievalService.RetrievedChunk(chunk(1L, "a"), 0.9),
                new RetrievalService.RetrievedChunk(chunk(2L, "b"), 0.8),
                new RetrievalService.RetrievedChunk(chunk(3L, "c"), 0.7)
        );
        List<RetrievalService.RetrievedChunk> keyword = List.of(
                new RetrievalService.RetrievedChunk(chunk(4L, "d"), 10.0),
                new RetrievalService.RetrievedChunk(chunk(5L, "e"), 8.0)
        );

        List<RetrievalService.RetrievedChunk> fused = rrfFusionService.fuse(semantic, keyword, 60, 3);

        assertEquals(3, fused.size());
    }

    @Test
    void shouldHandleBothListsEmpty() {
        List<RetrievalService.RetrievedChunk> fused = rrfFusionService.fuse(List.of(), List.of(), 60, 10);
        assertTrue(fused.isEmpty());
    }

    private ChunkEntity chunk(Long id, String text) {
        ChunkEntity c = new ChunkEntity();
        c.setId(id);
        c.setChunkText(text);
        c.setKbId(1L);
        c.setDocumentId(1L);
        c.setChunkNo(1);
        return c;
    }
}
