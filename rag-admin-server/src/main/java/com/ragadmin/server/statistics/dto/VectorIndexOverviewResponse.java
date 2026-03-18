package com.ragadmin.server.statistics.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VectorIndexOverviewResponse {

    private Long kbId;
    private String kbCode;
    private String kbName;
    private String kbStatus;
    private Long configuredEmbeddingModelId;
    private Long effectiveEmbeddingModelId;
    private String embeddingModelSource;
    private String embeddingModelCode;
    private String embeddingModelName;
    private Long documentCount;
    private Long successDocumentCount;
    private Long chunkCount;
    private Long vectorRefCount;
    private String collectionName;
    private Integer embeddingDim;
    private LocalDateTime latestVectorizedAt;
    private String milvusStatus;
    private String milvusMessage;
}
