package com.ragadmin.server.infra.elasticsearch;

import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.document.entity.ChunkEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class ElasticsearchClient {
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchClient.class);

    private final ElasticsearchProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ElasticsearchClient(ElasticsearchProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = RestClient.builder()
                .baseUrl(StringUtils.hasText(properties.getUris()) ? properties.getUris() : "http://127.0.0.1:9200")
                .requestFactory(requestFactory)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public void ensureIndex(String indexName) {
        if (!properties.isEnabled()) {
            return;
        }

        try {
            String mapping = """
                    {
                      "mappings": {
                        "properties": {
                          "chunk_text": {"type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart"},
                          "chunk_id": {"type": "long"},
                          "kb_id": {"type": "long"},
                          "document_id": {"type": "long"},
                          "document_version_id": {"type": "long"},
                          "enabled": {"type": "boolean"},
                          "metadata_json": {"type": "keyword"}
                        }
                      }
                    }
                    """;

            restClient.put()
                    .uri("/" + indexName)
                    .body(mapping)
                    .retrieve()
                    .onStatus(status -> status.value() == 400,
                            (request, response) -> {
                                log.info("Index {} already exists, skipping creation", indexName);
                            })
                    .toBodilessEntity();
        } catch (Exception e) {
            if (!e.getMessage().contains("already exists")) {
                log.warn("Failed to ensure index {}: {}", indexName, e.getMessage());
                throw new BusinessException("ES_ENSURE_INDEX_FAILED", "Failed to ensure index: " + indexName, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }

    public void bulkUpsert(String indexName, List<ChunkEntity> chunks) {
        if (!properties.isEnabled()) {
            return;
        }

        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        try {
            StringBuilder ndjson = new StringBuilder();
            for (ChunkEntity chunk : chunks) {
                String actionLine = String.format("{\"index\":{\"_index\":\"%s\",\"_id\":\"%d\"}}\n", indexName, chunk.getId());
                Map<String, Object> dataMap = new java.util.LinkedHashMap<>();
                dataMap.put("chunk_text", chunk.getChunkText());
                dataMap.put("chunk_id", chunk.getId());
                dataMap.put("kb_id", chunk.getKbId());
                dataMap.put("document_id", chunk.getDocumentId());
                dataMap.put("document_version_id", chunk.getDocumentVersionId());
                dataMap.put("enabled", chunk.getEnabled() != null ? chunk.getEnabled() : true);
                if (chunk.getMetadataJson() != null) {
                    dataMap.put("metadata_json", chunk.getMetadataJson());
                }
                String dataLine = objectMapper.writeValueAsString(dataMap) + "\n";
                ndjson.append(actionLine).append(dataLine);
            }

            String response = restClient.post()
                    .uri("/_bulk")
                    .body(ndjson.toString())
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.warn("Failed to bulk upsert chunks to index {}: {}", indexName, e.getMessage());
            throw new BusinessException("ES_BULK_UPSERT_FAILED", "Failed to bulk upsert chunks", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void deleteByDocumentId(String indexName, Long documentId) {
        if (!properties.isEnabled()) {
            return;
        }

        try {
            String queryBody = String.format("""
                    {"query":{"term":{"document_id":%d}}}
                    """, documentId);

            restClient.post()
                    .uri("/" + indexName + "/_delete_by_query")
                    .body(queryBody)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Failed to delete by documentId {} from index {}: {}", documentId, indexName, e.getMessage());
            throw new BusinessException("ES_DELETE_BY_DOC_FAILED", "Failed to delete by documentId", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void deleteIndex(String indexName) {
        if (!properties.isEnabled()) {
            return;
        }

        try {
            restClient.delete()
                    .uri("/" + indexName)
                    .retrieve()
                    .onStatus(status -> status.value() == 404,
                            (request, response) -> {
                                log.info("Index {} does not exist, skipping deletion", indexName);
                            })
                    .toBodilessEntity();
        } catch (Exception e) {
            if (!e.getMessage().contains("index_not_found_exception") && !e.getMessage().contains("404")) {
                log.warn("Failed to delete index {}: {}", indexName, e.getMessage());
                throw new BusinessException("ES_DELETE_INDEX_FAILED", "Failed to delete index: " + indexName, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }

    public List<Map<String, Object>> search(String indexName, String query, int limit) {
        if (!properties.isEnabled()) {
            return List.of();
        }

        try {
            String searchBody = String.format("""
                    {"query":{"match":{"chunk_text":{"query":"%s"}}},"size":%d}
                    """, query.replace("\"", "\\\""), limit);

            String response = restClient.post()
                    .uri("/" + indexName + "/_search")
                    .body(searchBody)
                    .retrieve()
                    .body(String.class);

            Map<String, Object> responseMap = objectMapper.readValue(response, new TypeReference<>() {});
            Map<String, Object> hits = (Map<String, Object>) responseMap.get("hits");
            List<Map<String, Object>> hitsList = (List<Map<String, Object>>) hits.get("hits");

            return hitsList.stream()
                    .map(hit -> (Map<String, Object>) hit.get("_source"))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to search index {} with query {}: {}", indexName, query, e.getMessage());
            throw new BusinessException("ES_SEARCH_FAILED", "Failed to search", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
