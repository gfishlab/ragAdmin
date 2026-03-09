package com.ragadmin.server.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ragadmin.server.document.entity.ChunkVectorRefEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ChunkVectorRefMapper extends BaseMapper<ChunkVectorRefEntity> {

    @Select("""
            <script>
            SELECT *
            FROM kb_chunk_vector_ref
            WHERE vector_id IN
            <foreach collection='vectorIds' item='vectorId' open='(' separator=',' close=')'>
                #{vectorId}
            </foreach>
            </script>
            """)
    List<ChunkVectorRefEntity> selectByVectorIds(@Param("vectorIds") List<String> vectorIds);
}
