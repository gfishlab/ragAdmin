package com.ragadmin.server.retrieval.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RrfFusionService {

    public List<RetrievalService.RetrievedChunk> fuse(
            List<RetrievalService.RetrievedChunk> semanticResults,
            List<RetrievalService.RetrievedChunk> keywordResults,
            int k,
            int maxResults) {

        Map<Long, Double> fusedScores = new LinkedHashMap<>();
        Map<Long, RetrievalService.RetrievedChunk> chunkMap = new LinkedHashMap<>();

        accumulateRanks(semanticResults, k, fusedScores, chunkMap);
        accumulateRanks(keywordResults, k, fusedScores, chunkMap);

        return fusedScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(maxResults)
                .map(entry -> {
                    RetrievalService.RetrievedChunk original = chunkMap.get(entry.getKey());
                    return new RetrievalService.RetrievedChunk(original.chunk(), entry.getValue());
                })
                .toList();
    }

    double rrfScore(int rank, int k) {
        return 1.0 / (k + rank);
    }

    private void accumulateRanks(
            List<RetrievalService.RetrievedChunk> results,
            int k,
            Map<Long, Double> fusedScores,
            Map<Long, RetrievalService.RetrievedChunk> chunkMap) {

        List<RetrievalService.RetrievedChunk> sorted = new ArrayList<>(results);
        sorted.sort(Comparator.comparing(RetrievalService.RetrievedChunk::score).reversed());

        for (int i = 0; i < sorted.size(); i++) {
            RetrievalService.RetrievedChunk chunk = sorted.get(i);
            long chunkId = chunk.chunk().getId();
            int rank = i + 1;
            double score = rrfScore(rank, k);

            fusedScores.merge(chunkId, score, Double::sum);
            chunkMap.putIfAbsent(chunkId, chunk);
        }
    }
}
