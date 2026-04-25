package com.ragadmin.server.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ragadmin.server.document.entity.ChunkEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;

public interface ChunkMapper extends BaseMapper<ChunkEntity> {

    @Insert("""
            INSERT INTO kb_chunk (
                kb_id,
                document_id,
                document_version_id,
                chunk_no,
                chunk_text,
                token_count,
                char_count,
                metadata_json,
                enabled,
                parent_chunk_id,
                chunk_strategy
            ) VALUES (
                #{kbId},
                #{documentId},
                #{documentVersionId},
                #{chunkNo},
                #{chunkText},
                #{tokenCount},
                #{charCount},
                CAST(#{metadataJson} AS jsonb),
                #{enabled},
                #{parentChunkId},
                #{chunkStrategy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertWithJsonb(ChunkEntity entity);
}
