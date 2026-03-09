package com.ragadmin.server.chat.controller;

import com.ragadmin.server.auth.model.AuthenticatedUser;
import com.ragadmin.server.auth.service.AuthService;
import com.ragadmin.server.chat.dto.ChatMessageResponse;
import com.ragadmin.server.chat.dto.ChatRequest;
import com.ragadmin.server.chat.dto.ChatResponse;
import com.ragadmin.server.chat.dto.ChatSessionResponse;
import com.ragadmin.server.chat.dto.CreateChatSessionRequest;
import com.ragadmin.server.chat.service.ChatService;
import com.ragadmin.server.common.model.ApiResponse;
import com.ragadmin.server.common.model.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/sessions")
    public ApiResponse<ChatSessionResponse> createSession(
            @Valid @RequestBody CreateChatSessionRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ApiResponse.success(chatService.createSession(request, currentUser(httpServletRequest)));
    }

    @GetMapping("/sessions")
    public ApiResponse<PageResponse<ChatSessionResponse>> listSessions(
            @RequestParam(required = false) Long kbId,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            HttpServletRequest httpServletRequest
    ) {
        return ApiResponse.success(chatService.listSessions(kbId, currentUser(httpServletRequest), pageNo, pageSize));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<ChatMessageResponse>> listMessages(
            @PathVariable Long sessionId,
            HttpServletRequest httpServletRequest
    ) {
        return ApiResponse.success(chatService.listMessages(sessionId, currentUser(httpServletRequest)));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ApiResponse<ChatResponse> chat(
            @PathVariable Long sessionId,
            @Valid @RequestBody ChatRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ApiResponse.success(chatService.chat(sessionId, request, currentUser(httpServletRequest)));
    }

    private AuthenticatedUser currentUser(HttpServletRequest request) {
        return (AuthenticatedUser) request.getAttribute(AuthService.REQUEST_ATTRIBUTE);
    }
}
