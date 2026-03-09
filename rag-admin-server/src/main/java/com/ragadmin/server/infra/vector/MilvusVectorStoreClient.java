package com.ragadmin.server.infra.vector;

import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.document.entity.ChunkEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class MilvusVectorStoreClient {

    private final MilvusProperties milvusProperties;
    private final RestClient restClient;

    public MilvusVectorStoreClient(MilvusProperties milvusProperties) {
        this.milvusProperties = milvusProperties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = RestClient.builder()
                .baseUrl(milvusProperties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + milvusProperties.getToken())
                .requestFactory(requestFactory)
                .build();
    }

    public void ensureCollection(String collectionName, int dimension) {
        if (!milvusProperties.isEnabled()) {
            return;
        }
        try {
            restClient.post()
                    .uri("/v2/vectordb/collections/create")
                    .body(Map.of(
                            "collectionName", collectionName,
                            "dimension", dimension,
                            "metricType", "COSINE",
                            "primaryFieldName", "id",
                            "vectorFieldName", "vector",
                            "idType", "VarChar",
                            "autoID", false
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ignore) {
            // collection 已存在时，Milvus 会返回错误；当前阶段视为可接受
        }
    }

    public void insert(String collectionName, List<Map<String, Object>> data) {
        upsertRaw(collectionName, data);
    }

    public void upsert(String collectionName, List<ChunkEntity> chunks, List<List<Float>> embeddings) {
        upsertRaw(collectionName, java.util.stream.IntStream.range(0, chunks.size())
                .mapToObj(index -> Map.<String, Object>of(
                        "id", "dv_" + chunks.get(index).getDocumentVersionId() + "_chunk_" + chunks.get(index).getChunkNo(),
                        "vector", embeddings.get(index),
                        "chunk_id", chunks.get(index).getId(),
                        "document_id", chunks.get(index).getDocumentId(),
                        "kb_id", chunks.get(index).getKbId()
                ))
                .toList());
    }

    public List<SearchResult> search(String collectionName, List<Float> vector, int limit) {
        if (!milvusProperties.isEnabled()) {
            return List.of();
        }
        try {
            SearchResponse response = restClient.post()
                    .uri("/v2/vectordb/entities/search")
                    .body(Map.of(
                            "collectionName", collectionName,
                            "data", List.of(vector),
                            "annsField", "vector",
                            "limit", limit,
                            "outputFields", List.of("id")
                    ))
                    .retrieve()
                    .body(SearchResponse.class);
            if (response == null || response.data() == null) {
                return List.of();
            }
            return response.data().stream()
                    .map(item -> new SearchResult(
                            String.valueOf(item.get("id")),
                            parseScore(item)
                    ))
                    .toList();
        } catch (Exception ex) {
            throw new BusinessException("MILVUS_SEARCH_FAILED", "Milvus 检索失败", HttpStatus.BAD_GATEWAY);
        }
    }

    private void upsertRaw(String collectionName, List<Map<String, Object>> data) {
        if (!milvusProperties.isEnabled()) {
            return;
        }
        try {
            restClient.post()
                    .uri("/v2/vectordb/entities/upsert")
                    .body(Map.of(
                            "collectionName", collectionName,
                            "data", data
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            throw new BusinessException("MILVUS_INSERT_FAILED", "Milvus 向量写入失败", HttpStatus.BAD_GATEWAY);
        }
    }

    private double parseScore(Map<String, Object> item) {
        Object score = item.getOrDefault("distance", item.get("score"));
        if (score instanceof Number number) {
            return number.doubleValue();
        }
        return 0D;
    }

    public record SearchResult(String vectorId, double score) {
    }

    private record SearchResponse(List<Map<String, Object>> data) {
    }
}
