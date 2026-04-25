package com.ragadmin.server.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragadmin.server.chat.dto.FeedbackListResponse;
import com.ragadmin.server.chat.entity.ChatFeedbackEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

public interface ChatFeedbackMapper extends BaseMapper<ChatFeedbackEntity> {

    @Select("""
            <script>
            SELECT cf.id, cf.message_id, cf.user_id, u.display_name AS username,
                   cf.feedback_type, cf.comment_text,
                   LEFT(cm.question_text, 100) AS question_summary,
                   LEFT(cm.answer_text, 100) AS answer_summary,
                   cm.session_id, cf.created_at
            FROM chat_feedback cf
            LEFT JOIN chat_message cm ON cm.id = cf.message_id
            LEFT JOIN sys_user u ON u.id = cf.user_id
            <where>
                <if test='feedbackType != null and feedbackType != ""'>
                    AND cf.feedback_type = #{feedbackType}
                </if>
                <if test='startTime != null'>
                    AND cf.created_at &gt;= #{startTime}
                </if>
                <if test='endTime != null'>
                    AND cf.created_at &lt;= #{endTime}
                </if>
            </where>
            ORDER BY cf.id DESC
            </script>
            """)
    Page<FeedbackListResponse> selectFeedbackPage(Page<FeedbackListResponse> page,
                                                  @Param("feedbackType") String feedbackType,
                                                  @Param("startTime") LocalDateTime startTime,
                                                  @Param("endTime") LocalDateTime endTime);
}
