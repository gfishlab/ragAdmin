package com.ragadmin.server.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateChatSessionRequest {

    @NotNull(message = "kbId 不能为空")
    private Long kbId;

    @NotBlank(message = "sessionName 不能为空")
    private String sessionName;

    public Long getKbId() {
        return kbId;
    }

    public void setKbId(Long kbId) {
        this.kbId = kbId;
    }

    public String getSessionName() {
        return sessionName;
    }

    public void setSessionName(String sessionName) {
        this.sessionName = sessionName;
    }
}
