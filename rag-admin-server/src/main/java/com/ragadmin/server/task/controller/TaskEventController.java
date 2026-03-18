package com.ragadmin.server.task.controller;

import com.ragadmin.server.task.service.TaskRealtimeEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/admin/events")
public class TaskEventController {

    @Autowired
    private TaskRealtimeEventService taskRealtimeEventService;

    @GetMapping(value = "/knowledge-bases/{kbId}/documents", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeKnowledgeBaseDocuments(@PathVariable Long kbId) {
        return taskRealtimeEventService.subscribeKnowledgeBase(kbId);
    }

    @GetMapping(value = "/documents/{documentId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeDocument(@PathVariable Long documentId) {
        return taskRealtimeEventService.subscribeDocument(documentId);
    }

    @GetMapping(value = "/tasks", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeTasks() {
        return taskRealtimeEventService.subscribeTasks();
    }
}
