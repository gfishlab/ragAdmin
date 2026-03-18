package com.ragadmin.server.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateChatSessionRequest {

    private Long kbId;

    /**
     * 默认兼容历史行为，未显式传值时按知识库会话处理。
     */
    private String sceneType;

    @NotBlank(message = "sessionName 不能为空")
    private String sessionName;
}
