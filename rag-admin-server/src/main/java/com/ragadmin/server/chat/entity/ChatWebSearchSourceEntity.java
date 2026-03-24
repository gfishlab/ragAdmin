package com.ragadmin.server.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_web_search_source")
public class ChatWebSearchSourceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long messageId;
    private String title;
    private String sourceUrl;
    private LocalDateTime publishedAt;
    private String snippet;
    private Integer rankNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
