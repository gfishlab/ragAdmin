package com.ragadmin.server.statistics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ragadmin.server.knowledge.entity.KnowledgeBaseEntity;
import com.ragadmin.server.statistics.dto.VectorIndexCollectionStatRow;
import com.ragadmin.server.statistics.dto.VectorIndexOverviewResponse;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface VectorIndexOverviewMapper extends BaseMapper<KnowledgeBaseEntity> {

    @Select("""
            <script>
            SELECT
                kb.id AS "kbId",
                kb.kb_code AS "kbCode",
                kb.kb_name AS "kbName",
                kb.status AS "kbStatus",
                kb.embedding_model_id AS "configuredEmbeddingModelId",
                COALESCE((SELECT COUNT(*) FROM kb_document d WHERE d.kb_id = kb.id), 0) AS "documentCount",
                COALESCE((SELECT COUNT(*) FROM kb_document d WHERE d.kb_id = kb.id AND d.parse_status = 'SUCCESS'), 0) AS "successDocumentCount",
                COALESCE((SELECT COUNT(*) FROM kb_chunk c WHERE c.kb_id = kb.id), 0) AS "chunkCount"
            FROM kb_knowledge_base kb
            <where>
                <if test='keyword != null and keyword != ""'>
                    AND (kb.kb_code LIKE CONCAT('%', #{keyword}, '%')
                    OR kb.kb_name LIKE CONCAT('%', #{keyword}, '%'))
                </if>
                <if test='status != null and status != ""'>
                    AND kb.status = #{status}
                </if>
            </where>
            ORDER BY kb.id DESC
            </script>
            """)
    List<VectorIndexOverviewResponse> selectOverview(
            @Param("keyword") String keyword,
            @Param("status") String status
    );

    @Select("""
            <script>
            SELECT
                r.kb_id AS "kbId",
                r.embedding_model_id AS "embeddingModelId",
                COUNT(*) AS "vectorRefCount",
                MAX(r.collection_name) AS "collectionName",
                MAX(r.embedding_dim) AS "embeddingDim",
                MAX(r.created_at) AS "latestVectorizedAt"
            FROM kb_chunk_vector_ref r
            WHERE r.status = 'ENABLED'
            <if test='kbIds != null and kbIds.size() > 0'>
                AND r.kb_id IN
                <foreach collection='kbIds' item='kbId' open='(' separator=',' close=')'>
                    #{kbId}
                </foreach>
            </if>
            GROUP BY r.kb_id, r.embedding_model_id
            </script>
            """)
    List<VectorIndexCollectionStatRow> selectVectorStats(@Param("kbIds") List<Long> kbIds);
}
