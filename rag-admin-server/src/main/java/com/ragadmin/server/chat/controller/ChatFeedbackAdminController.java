package com.ragadmin.server.chat.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.ragadmin.server.chat.dto.FeedbackListResponse;
import com.ragadmin.server.chat.service.ChatFeedbackAdminService;
import com.ragadmin.server.common.model.ApiResponse;
import com.ragadmin.server.common.model.PageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/chat-feedback")
@SaCheckLogin(type = "admin")
@SaCheckPermission("CHAT_FEEDBACK_VIEW")
public class ChatFeedbackAdminController {

    @Autowired
    private ChatFeedbackAdminService chatFeedbackAdminService;

    @GetMapping
    public ApiResponse<PageResponse<FeedbackListResponse>> list(
            @RequestParam(required = false) String feedbackType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize
    ) {
        return ApiResponse.success(chatFeedbackAdminService.listFeedback(feedbackType, startTime, endTime, pageNo, pageSize));
    }
}
