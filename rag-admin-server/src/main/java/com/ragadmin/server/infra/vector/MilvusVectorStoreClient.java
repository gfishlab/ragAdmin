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
import java.util.Objects;

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
            Map<String, Object> response = restClient.post()
                    .uri("/v2/vectordb/collections/create")
                    .body(Map.of(
                            "collectionName", collectionName,
                            "dimension", dimension,
                            "metricType", "COSINE",
                            "primaryFieldName", "id",
                            "vectorFieldName", "vector",
                            "idType", "VarChar",
                            "autoID", false,
                            // VarChar 主键必须显式给出 max_length，否则 Milvus 会返回业务错误码但 HTTP 仍是 200。
                            "params", Map.of("max_length", "128")
                    ))
                    .retrieve()
                    .body(Map.class);
            if (isSuccess(response)) {
                return;
            }
            String message = responseMessage(response);
            if (message.contains("already") || message.contains("exist")) {
                return;
            }
            throw new BusinessException("MILVUS_COLLECTION_CREATE_FAILED", "Milvus 集合创建失败: " + message, HttpStatus.BAD_GATEWAY);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("MILVUS_COLLECTION_CREATE_FAILED", "Milvus 集合创建失败", HttpStatus.BAD_GATEWAY);
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
            Map<String, Object> response = restClient.post()
                    .uri("/v2/vectordb/entities/search")
                    .body(Map.of(
                            "collectionName", collectionName,
                            "data", List.of(vector),
                            "annsField", "vector",
                            "limit", limit,
                            "outputFields", List.of("id")
                    ))
                    .retrieve()
                    .body(Map.class);
            if (!isSuccess(response)) {
                throw new BusinessException("MILVUS_SEARCH_FAILED", "Milvus 检索失败: " + responseMessage(response), HttpStatus.BAD_GATEWAY);
            }
            Object data = response == null ? null : response.get("data");
            if (!(data instanceof List<?> rows) || rows.isEmpty()) {
                return List.of();
            }
            return rows.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .map(item -> new SearchResult(
                            String.valueOf(item.get("id")),
                            parseScore(item)
                    ))
                    .toList();
        } catch (Exception ex) {
            throw new BusinessException("MILVUS_SEARCH_FAILED", "Milvus 检索失败", HttpStatus.BAD_GATEWAY);
        }
    }

    public CollectionDescription describeCollection(String collectionName) {
        if (!milvusProperties.isEnabled()) {
            return new CollectionDescription(collectionName, "DISABLED", null, null);
        }
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/v2/vectordb/collections/describe")
                    .body(Map.of("collectionName", collectionName))
                    .retrieve()
                    .body(Map.class);
            if (!isSuccess(response)) {
                throw new BusinessException("MILVUS_COLLECTION_DESCRIBE_FAILED", "Milvus 集合描述失败: " + responseMessage(response), HttpStatus.BAD_GATEWAY);
            }
            Object data = response == null ? null : response.get("data");
            if (!(data instanceof Map<?, ?> map)) {
                throw new BusinessException("MILVUS_COLLECTION_DESCRIBE_FAILED", "Milvus 集合描述失败: 响应缺少 data", HttpStatus.BAD_GATEWAY);
            }
            String loadState = Objects.toString(map.get("load"), "");
            Integer dimension = extractDimension(map.get("fields"));
            String metricType = extractMetricType(map.get("indexes"));
            return new CollectionDescription(collectionName, loadState, dimension, metricType);
        } catch (Exception ex) {
            if (ex instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException("MILVUS_COLLECTION_DESCRIBE_FAILED", "Milvus 集合描述失败", HttpStatus.BAD_GATEWAY);
        }
    }

    private void upsertRaw(String collectionName, List<Map<String, Object>> data) {
        if (!milvusProperties.isEnabled()) {
            return;
        }
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/v2/vectordb/entities/upsert")
                    .body(Map.of(
                            "collectionName", collectionName,
                            "data", data
                    ))
                    .retrieve()
                    .body(Map.class);
            if (!isSuccess(response)) {
                throw new BusinessException("MILVUS_INSERT_FAILED", "Milvus 向量写入失败: " + responseMessage(response), HttpStatus.BAD_GATEWAY);
            }
        } catch (Exception ex) {
            if (ex instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException("MILVUS_INSERT_FAILED", "Milvus 向量写入失败", HttpStatus.BAD_GATEWAY);
        }
    }

    private boolean isSuccess(Map<String, Object> response) {
        if (response == null) {
            return false;
        }
        Object code = response.get("code");
        if (code instanceof Number number) {
            return number.intValue() == 0;
        }
        return "0".equals(Objects.toString(code, null));
    }

    private String responseMessage(Map<String, Object> response) {
        if (response == null) {
            return "空响应";
        }
        return Objects.toString(response.get("message"), "未知错误");
    }

    private double parseScore(Map<String, Object> item) {
        Object score = item.getOrDefault("distance", item.get("score"));
        if (score instanceof Number number) {
            return number.doubleValue();
        }
        return 0D;
    }

    private Integer extractDimension(Object fieldObject) {
        if (!(fieldObject instanceof List<?> fields)) {
            return null;
        }
        return fields.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(field -> "vector".equals(Objects.toString(field.get("name"), null)))
                .findFirst()
                .map(field -> extractIntegerFromParams(field.get("params"), "dim"))
                .orElse(null);
    }

    private String extractMetricType(Object indexObject) {
        if (!(indexObject instanceof List<?> indexes) || indexes.isEmpty()) {
            return null;
        }
        Object first = indexes.getFirst();
        if (!(first instanceof Map<?, ?> map)) {
            return null;
        }
        return Objects.toString(map.get("metricType"), null);
    }

    private Integer extractIntegerFromParams(Object paramsObject, String key) {
        if (!(paramsObject instanceof List<?> params)) {
            return null;
        }
        return params.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(item -> key.equals(Objects.toString(item.get("key"), null)))
                .findFirst()
                .map(item -> {
                    Object value = item.get("value");
                    if (value instanceof Number number) {
                        return number.intValue();
                    }
                    try {
                        return value == null ? null : Integer.parseInt(String.valueOf(value));
                    } catch (NumberFormatException ex) {
                        return null;
                    }
                })
                .orElse(null);
    }

    public record SearchResult(String vectorId, double score) {
    }

    public record CollectionDescription(
            String collectionName,
            String loadState,
            Integer dimension,
            String metricType
    ) {
    }
}
