package com.ragadmin.server.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ragadmin.server.task.entity.TaskStepRecordEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface TaskStepRecordMapper extends BaseMapper<TaskStepRecordEntity> {

    @Update("""
            UPDATE job_task_step_record
            SET detail_json = CAST(#{detailJson} AS jsonb)
            WHERE id = #{id}
            """)
    int updateDetailJson(@Param("id") Long id, @Param("detailJson") String detailJson);
}
