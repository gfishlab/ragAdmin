package com.ragadmin.server.statistics.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VectorIndexCollectionStatRow {

    private Long kbId;
    private Long embeddingModelId;
    private Long vectorRefCount;
    private String collectionName;
    private Integer embeddingDim;
    private LocalDateTime latestVectorizedAt;
}
