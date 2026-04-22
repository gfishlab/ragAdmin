package com.ragadmin.server.retrieval.service;

import com.ragadmin.server.document.entity.ChunkEntity;
import com.ragadmin.server.document.mapper.ChunkMapper;
import com.ragadmin.server.infra.elasticsearch.ElasticsearchClient;
import com.ragadmin.server.infra.elasticsearch.ElasticsearchProperties;
import com.ragadmin.server.knowledge.entity.KnowledgeBaseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class KeywordRetrievalStrategy {

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private ElasticsearchProperties elasticsearchProperties;

    @Autowired
    private ChunkMapper chunkMapper;

    public List<RetrievalService.RetrievedChunk> retrieve(KnowledgeBaseEntity knowledgeBase, String query, int topK) {
        if (!elasticsearchProperties.isEnabled()) {
            return List.of();
        }

        String indexName = "kb_" + knowledgeBase.getId() + "_chunks";
        List<ElasticsearchClient.ScoredDocument> results = elasticsearchClient.searchWithScores(indexName, query, topK);

        if (results.isEmpty()) {
            return List.of();
        }

        List<Long> chunkIds = results.stream()
                .map(ElasticsearchClient.ScoredDocument::chunkId)
                .distinct()
                .toList();

        Map<Long, ChunkEntity> chunkMap = chunkMapper.selectBatchIds(chunkIds).stream()
                .collect(Collectors.toMap(ChunkEntity::getId, Function.identity()));

        return results.stream()
                .filter(doc -> chunkMap.containsKey(doc.chunkId()))
                .map(doc -> new RetrievalService.RetrievedChunk(chunkMap.get(doc.chunkId()), doc.score()))
                .sorted(Comparator.comparing(RetrievalService.RetrievedChunk::score).reversed())
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                item -> item.chunk().getId(),
                                Function.identity(),
                                (left, right) -> left,
                                LinkedHashMap::new
                        ),
                        map -> map.values().stream().toList()
                ));
    }
}
