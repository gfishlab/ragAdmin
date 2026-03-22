package com.ragadmin.server.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.entity.DocumentParseTaskEntity;
import com.ragadmin.server.document.mapper.DocumentMapper;
import com.ragadmin.server.document.mapper.DocumentParseTaskMapper;
import com.ragadmin.server.task.dto.TaskRealtimeEventResponse;
import com.ragadmin.server.task.entity.TaskStepRecordEntity;
import com.ragadmin.server.task.mapper.TaskStepRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.DisconnectedClientHelper;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class TaskRealtimeEventService {

    private static final Logger log = LoggerFactory.getLogger(TaskRealtimeEventService.class);
    private static final DisconnectedClientHelper DISCONNECTED_CLIENT_HELPER =
            new DisconnectedClientHelper(TaskRealtimeEventService.class.getName());
    private static final long SSE_TIMEOUT_MS = 0L;
    private static final String EVENT_NAME = "task-realtime";
    private static final String EVENT_TYPE_CONNECTED = "CONNECTED";

    private final DocumentParseTaskMapper documentParseTaskMapper;
    private final DocumentMapper documentMapper;
    private final TaskStepRecordMapper taskStepRecordMapper;

    private final Map<Long, Set<SseEmitter>> knowledgeBaseEmitters = new ConcurrentHashMap<>();
    private final Map<Long, Set<SseEmitter>> documentEmitters = new ConcurrentHashMap<>();
    private final Set<SseEmitter> taskEmitters = ConcurrentHashMap.newKeySet();

    public TaskRealtimeEventService(
            DocumentParseTaskMapper documentParseTaskMapper,
            DocumentMapper documentMapper,
            TaskStepRecordMapper taskStepRecordMapper
    ) {
        this.documentParseTaskMapper = documentParseTaskMapper;
        this.documentMapper = documentMapper;
        this.taskStepRecordMapper = taskStepRecordMapper;
    }

    public SseEmitter subscribeKnowledgeBase(Long kbId) {
        SseEmitter emitter = createEmitter();
        Set<SseEmitter> emitters = knowledgeBaseEmitters.computeIfAbsent(kbId, key -> ConcurrentHashMap.newKeySet());
        registerEmitter(emitters, emitter);
        sendConnectedEvent(emitters, emitter, "知识库文档解析实时通道已连接");
        sendSnapshotEvents(emitters, emitter, listActiveEventsByKnowledgeBase(kbId));
        return emitter;
    }

    public SseEmitter subscribeDocument(Long documentId) {
        SseEmitter emitter = createEmitter();
        Set<SseEmitter> emitters = documentEmitters.computeIfAbsent(documentId, key -> ConcurrentHashMap.newKeySet());
        registerEmitter(emitters, emitter);
        sendConnectedEvent(emitters, emitter, "文档解析实时通道已连接");
        sendSnapshotEvents(emitters, emitter, listActiveEventsByDocument(documentId));
        return emitter;
    }

    public SseEmitter subscribeTasks() {
        SseEmitter emitter = createEmitter();
        registerEmitter(taskEmitters, emitter);
        sendConnectedEvent(taskEmitters, emitter, "任务实时通道已连接");
        sendSnapshotEvents(taskEmitters, emitter, listActiveEvents());
        return emitter;
    }

    public void publishTaskQueued(DocumentParseTaskEntity task, DocumentEntity document) {
        publish(buildEvent("TASK_QUEUED", task, document, "WAITING", "排队中", "解析任务已进入队列"));
    }

    public void publishTaskStarted(DocumentParseTaskEntity task, DocumentEntity document) {
        publish(buildEvent("TASK_STARTED", task, document, "RUNNING", "准备执行", "解析任务已开始执行"));
    }

    public void publishStepStarted(
            DocumentParseTaskEntity task,
            DocumentEntity document,
            String stepCode,
            String stepName
    ) {
        publish(buildEvent("STEP_STARTED", task, document, stepCode, stepName, "当前阶段开始执行"));
    }

    public void publishStepCompleted(
            DocumentParseTaskEntity task,
            DocumentEntity document,
            String stepCode,
            String stepName
    ) {
        publish(buildEvent("STEP_COMPLETED", task, document, stepCode, stepName, "当前阶段执行完成"));
    }

    public void publishTaskSucceeded(DocumentParseTaskEntity task, DocumentEntity document) {
        publish(buildEvent("TASK_SUCCEEDED", task, document, "SUCCESS", "解析完成", "文档解析已完成"));
    }

    public void publishTaskFailed(
            DocumentParseTaskEntity task,
            DocumentEntity document,
            String currentStepCode,
            String currentStepName
    ) {
        String message = task.getErrorMessage();
        if (message == null || message.isBlank()) {
            message = "文档解析失败";
        }
        publish(buildEvent("TASK_FAILED", task, document, currentStepCode, currentStepName, message));
    }

    private void publish(TaskRealtimeEventResponse event) {
        sendToEmitters(taskEmitters, event);
        if (event.kbId() != null) {
            sendToEmitters(knowledgeBaseEmitters.get(event.kbId()), event);
        }
        if (event.documentId() != null) {
            sendToEmitters(documentEmitters.get(event.documentId()), event);
        }
    }

    private SseEmitter createEmitter() {
        return new SseEmitter(SSE_TIMEOUT_MS);
    }

    private void registerEmitter(Set<SseEmitter> emitters, SseEmitter emitter) {
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(ex -> emitters.remove(emitter));
    }

    private void sendConnectedEvent(Set<SseEmitter> emitters, SseEmitter emitter, String message) {
        TaskRealtimeEventResponse event = new TaskRealtimeEventResponse(
                EVENT_TYPE_CONNECTED,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                message,
                false,
                LocalDateTime.now()
        );
        send(emitters, emitter, event);
    }

    private void sendSnapshotEvents(Set<SseEmitter> emitters, SseEmitter emitter, List<TaskRealtimeEventResponse> events) {
        for (TaskRealtimeEventResponse event : events) {
            send(emitters, emitter, event);
        }
    }

    private void sendToEmitters(Set<SseEmitter> emitters, TaskRealtimeEventResponse event) {
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            send(emitters, emitter, event);
        }
    }

    private void send(Set<SseEmitter> emitters, SseEmitter emitter, TaskRealtimeEventResponse event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(EVENT_NAME)
                    .data(event));
        } catch (IOException | IllegalStateException ex) {
            removeEmitter(emitters, emitter, ex);
        }
    }

    private void removeEmitter(Set<SseEmitter> emitters, SseEmitter emitter, Exception ex) {
        if (emitters != null) {
            emitters.remove(emitter);
        }
        if (DISCONNECTED_CLIENT_HELPER.checkAndLogClientDisconnectedException(ex)) {
            return;
        }
        log.debug("SSE 连接已不可用，已移除连接，type={}, message={}",
                ex.getClass().getSimpleName(), ex.getMessage());
    }

    private List<TaskRealtimeEventResponse> listActiveEventsByKnowledgeBase(Long kbId) {
        List<DocumentParseTaskEntity> tasks = documentParseTaskMapper.selectList(new LambdaQueryWrapper<DocumentParseTaskEntity>()
                .eq(DocumentParseTaskEntity::getKbId, kbId)
                .in(DocumentParseTaskEntity::getTaskStatus, "WAITING", "RUNNING")
                .orderByAsc(DocumentParseTaskEntity::getId)
                .last("LIMIT 50"));
        return toActiveEvents(tasks);
    }

    private List<TaskRealtimeEventResponse> listActiveEventsByDocument(Long documentId) {
        List<DocumentParseTaskEntity> tasks = documentParseTaskMapper.selectList(new LambdaQueryWrapper<DocumentParseTaskEntity>()
                .eq(DocumentParseTaskEntity::getDocumentId, documentId)
                .in(DocumentParseTaskEntity::getTaskStatus, "WAITING", "RUNNING")
                .orderByAsc(DocumentParseTaskEntity::getId)
                .last("LIMIT 10"));
        return toActiveEvents(tasks);
    }

    private List<TaskRealtimeEventResponse> listActiveEvents() {
        List<DocumentParseTaskEntity> tasks = documentParseTaskMapper.selectList(new LambdaQueryWrapper<DocumentParseTaskEntity>()
                .in(DocumentParseTaskEntity::getTaskStatus, "WAITING", "RUNNING")
                .orderByDesc(DocumentParseTaskEntity::getId)
                .last("LIMIT 100"));
        return toActiveEvents(tasks);
    }

    private List<TaskRealtimeEventResponse> toActiveEvents(List<DocumentParseTaskEntity> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        Map<Long, DocumentEntity> documentMap = documentMapper.selectBatchIds(tasks.stream()
                        .map(DocumentParseTaskEntity::getDocumentId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(DocumentEntity::getId, item -> item));

        return tasks.stream()
                .map(task -> {
                    DocumentEntity document = documentMap.get(task.getDocumentId());
                    TaskStepRecordEntity currentStep = findCurrentStep(task.getId());
                    String currentStepCode = currentStep == null ? null : currentStep.getStepCode();
                    String currentStepName = currentStep == null ? null : currentStep.getStepName();
                    return new TaskRealtimeEventResponse(
                            "SNAPSHOT",
                            task.getId(),
                            task.getKbId(),
                            task.getDocumentId(),
                            document == null ? null : document.getDocName(),
                            task.getTaskStatus(),
                            document == null ? null : document.getParseStatus(),
                            currentStepCode,
                            currentStepName,
                            resolveProgressPercent(task.getTaskStatus(), currentStepCode),
                            resolveSnapshotMessage(task.getTaskStatus(), currentStepName),
                            false,
                            LocalDateTime.now()
                    );
                })
                .toList();
    }

    private TaskRealtimeEventResponse buildEvent(
            String eventType,
            DocumentParseTaskEntity task,
            DocumentEntity document,
            String currentStepCode,
            String currentStepName,
            String message
    ) {
        return new TaskRealtimeEventResponse(
                eventType,
                task.getId(),
                task.getKbId(),
                task.getDocumentId(),
                document == null ? null : document.getDocName(),
                task.getTaskStatus(),
                document == null ? null : document.getParseStatus(),
                currentStepCode,
                currentStepName,
                resolveProgressPercent(task.getTaskStatus(), currentStepCode),
                message,
                isTerminal(task.getTaskStatus()),
                LocalDateTime.now()
        );
    }

    private TaskStepRecordEntity findCurrentStep(Long taskId) {
        TaskStepRecordEntity runningStep = taskStepRecordMapper.selectOne(new LambdaQueryWrapper<TaskStepRecordEntity>()
                .eq(TaskStepRecordEntity::getTaskId, taskId)
                .eq(TaskStepRecordEntity::getStepStatus, "RUNNING")
                .orderByDesc(TaskStepRecordEntity::getId)
                .last("LIMIT 1"));
        if (runningStep != null) {
            return runningStep;
        }
        return taskStepRecordMapper.selectOne(new LambdaQueryWrapper<TaskStepRecordEntity>()
                .eq(TaskStepRecordEntity::getTaskId, taskId)
                .orderByDesc(TaskStepRecordEntity::getId)
                .last("LIMIT 1"));
    }

    private Integer resolveProgressPercent(String taskStatus, String currentStepCode) {
        if ("SUCCESS".equals(taskStatus)) {
            return 100;
        }
        if ("WAITING".equals(taskStatus)) {
            return 5;
        }
        if ("EXTRACT_TEXT".equals(currentStepCode)) {
            return 30;
        }
        if ("PERSIST_CHUNKS".equals(currentStepCode)) {
            return 65;
        }
        if ("GENERATE_EMBEDDING".equals(currentStepCode)) {
            return 90;
        }
        if ("FAILED".equals(taskStatus) || "CANCELED".equals(taskStatus)) {
            return currentStepCode == null ? 5 : resolveProgressPercent("RUNNING", currentStepCode);
        }
        return 15;
    }

    private String resolveSnapshotMessage(String taskStatus, String currentStepName) {
        if ("WAITING".equals(taskStatus)) {
            return "解析任务正在排队";
        }
        if (currentStepName != null && !currentStepName.isBlank()) {
            return currentStepName + "进行中";
        }
        return "解析任务执行中";
    }

    private boolean isTerminal(String taskStatus) {
        return "SUCCESS".equals(taskStatus) || "FAILED".equals(taskStatus) || "CANCELED".equals(taskStatus);
    }
}
