package com.ragadmin.server.chat.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragadmin.server.chat.dto.FeedbackListResponse;
import com.ragadmin.server.chat.mapper.ChatFeedbackMapper;
import com.ragadmin.server.common.model.PageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ChatFeedbackAdminService {

    @Autowired
    private ChatFeedbackMapper chatFeedbackMapper;

    public PageResponse<FeedbackListResponse> listFeedback(
            String feedbackType,
            LocalDateTime startTime,
            LocalDateTime endTime,
            long pageNo,
            long pageSize
    ) {
        Page<FeedbackListResponse> page = chatFeedbackMapper.selectFeedbackPage(
                Page.of(pageNo, pageSize), feedbackType, startTime, endTime);
        return new PageResponse<>(page.getRecords(), pageNo, pageSize, page.getTotal());
    }
}
