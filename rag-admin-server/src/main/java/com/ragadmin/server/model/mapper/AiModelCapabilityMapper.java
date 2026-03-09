package com.ragadmin.server.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ragadmin.server.model.entity.AiModelCapabilityEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AiModelCapabilityMapper extends BaseMapper<AiModelCapabilityEntity> {

    @Select("""
            SELECT model_id
            FROM ai_model_capability
            WHERE enabled = TRUE
            AND capability_type = #{capabilityType}
            """)
    List<Long> selectModelIdsByCapabilityType(@Param("capabilityType") String capabilityType);

    @Select("""
            <script>
            SELECT model_id, capability_type
            FROM ai_model_capability
            WHERE enabled = TRUE
            AND model_id IN
            <foreach collection='modelIds' item='modelId' open='(' separator=',' close=')'>
                #{modelId}
            </foreach>
            ORDER BY id
            </script>
            """)
    List<AiModelCapabilityEntity> selectEnabledByModelIds(@Param("modelIds") List<Long> modelIds);
}
