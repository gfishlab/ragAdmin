package com.ragadmin.server.document.support;

import com.ragadmin.server.document.entity.ChunkEntity;
import com.ragadmin.server.infra.elasticsearch.ElasticsearchClient;
import com.ragadmin.server.infra.elasticsearch.ElasticsearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChunkSearchSyncService {

    private static final Logger log = LoggerFactory.getLogger(ChunkSearchSyncService.class);

    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchProperties elasticsearchProperties;

    public ChunkSearchSyncService(ElasticsearchClient elasticsearchClient,
                                   ElasticsearchProperties elasticsearchProperties) {
        this.elasticsearchClient = elasticsearchClient;
        this.elasticsearchProperties = elasticsearchProperties;
    }

    private String indexName(Long kbId) {
        return "kb_" + kbId + "_chunks";
    }

    public void syncChunks(Long kbId, List<ChunkEntity> chunks) {
        if (!elasticsearchProperties.isEnabled()) {
            return;
        }
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        String idx = indexName(kbId);
        elasticsearchClient.ensureIndex(idx);
        elasticsearchClient.bulkUpsert(idx, chunks);
        log.info("ES 同步完成，index={}, chunkCount={}", idx, chunks.size());
    }

    public void deleteByDocument(Long kbId, Long documentId) {
        if (!elasticsearchProperties.isEnabled()) {
            return;
        }
        String idx = indexName(kbId);
        elasticsearchClient.deleteByDocumentId(idx, documentId);
        log.info("ES 文档删除完成，index={}, documentId={}", idx, documentId);
    }

    public void deleteByKnowledgeBase(Long kbId) {
        if (!elasticsearchProperties.isEnabled()) {
            return;
        }
        String idx = indexName(kbId);
        elasticsearchClient.deleteIndex(idx);
        log.info("ES 索引删除完成，index={}", idx);
    }
}
