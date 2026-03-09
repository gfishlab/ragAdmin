package com.ragadmin.server.chat.dto;

import jakarta.validation.constraints.NotBlank;

public class ChatFeedbackRequest {

    @NotBlank(message = "feedbackType 不能为空")
    private String feedbackType;

    private String comment;

    public String getFeedbackType() {
        return feedbackType;
    }

    public void setFeedbackType(String feedbackType) {
        this.feedbackType = feedbackType;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
