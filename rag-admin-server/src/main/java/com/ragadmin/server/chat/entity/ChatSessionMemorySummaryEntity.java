package com.ragadmin.server.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_session_memory_summary")
public class ChatSessionMemorySummaryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private String conversationId;
    private String summaryText;
    private Integer summaryVersion;
    private Integer compressedMessageCount;
    private Long compressedUntilMessageId;
    private Long lastSourceMessageId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
