package com.ragadmin.server.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.entity.DocumentParseTaskEntity;
import com.ragadmin.server.document.mapper.DocumentMapper;
import com.ragadmin.server.document.mapper.DocumentParseTaskMapper;
import com.ragadmin.server.task.dto.TaskRealtimeEventResponse;
import com.ragadmin.server.task.entity.TaskStepRecordEntity;
import com.ragadmin.server.task.mapper.TaskStepRecordMapper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class TaskRealtimeEventService {

    private static final String EVENT_TYPE_CONNECTED = "CONNECTED";

    private final DocumentParseTaskMapper documentParseTaskMapper;
    private final DocumentMapper documentMapper;
    private final TaskStepRecordMapper taskStepRecordMapper;

    private final Map<Long, Set<TaskEventSubscriber>> knowledgeBaseSubscribers = new ConcurrentHashMap<>();
    private final Map<Long, Set<TaskEventSubscriber>> documentSubscribers = new ConcurrentHashMap<>();
    private final Set<TaskEventSubscriber> taskSubscribers = ConcurrentHashMap.newKeySet();

    public TaskRealtimeEventService(
            DocumentParseTaskMapper documentParseTaskMapper,
            DocumentMapper documentMapper,
            TaskStepRecordMapper taskStepRecordMapper
    ) {
        this.documentParseTaskMapper = documentParseTaskMapper;
        this.documentMapper = documentMapper;
        this.taskStepRecordMapper = taskStepRecordMapper;
    }

    public Flux<TaskRealtimeEventResponse> subscribeKnowledgeBase(Long kbId) {
        return subscribe(
                knowledgeBaseSubscribers.computeIfAbsent(kbId, key -> ConcurrentHashMap.newKeySet()),
                () -> knowledgeBaseSubscribers.computeIfPresent(kbId, (key, subscribers) -> subscribers.isEmpty() ? null : subscribers),
                "知识库文档解析实时通道已连接",
                () -> listActiveEventsByKnowledgeBase(kbId)
        );
    }

    public Flux<TaskRealtimeEventResponse> subscribeDocument(Long documentId) {
        return subscribe(
                documentSubscribers.computeIfAbsent(documentId, key -> ConcurrentHashMap.newKeySet()),
                () -> documentSubscribers.computeIfPresent(documentId, (key, subscribers) -> subscribers.isEmpty() ? null : subscribers),
                "文档解析实时通道已连接",
                () -> listActiveEventsByDocument(documentId)
        );
    }

    public Flux<TaskRealtimeEventResponse> subscribeTasks() {
        return subscribe(
                taskSubscribers,
                () -> {
                },
                "任务实时通道已连接",
                this::listActiveEvents
        );
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
        emitToSubscribers(taskSubscribers, event);
        if (event.kbId() != null) {
            emitToSubscribers(knowledgeBaseSubscribers.get(event.kbId()), event);
        }
        if (event.documentId() != null) {
            emitToSubscribers(documentSubscribers.get(event.documentId()), event);
        }
    }

    private Flux<TaskRealtimeEventResponse> subscribe(
            Set<TaskEventSubscriber> subscribers,
            Runnable cleanupAction,
            String connectedMessage,
            Supplier<List<TaskRealtimeEventResponse>> snapshotSupplier
    ) {
        return Flux.defer(() -> {
            BufferedTaskEventSubscriber subscriber = new BufferedTaskEventSubscriber();
            subscribers.add(subscriber);
            try {
                subscriber.emitSnapshot(buildConnectedEvent(connectedMessage));
                snapshotSupplier.get().forEach(subscriber::emitSnapshot);
                subscriber.completeSnapshot();
            } catch (Exception ex) {
                subscribers.remove(subscriber);
                cleanupAction.run();
                subscriber.complete();
                return Flux.error(ex);
            }
            return subscriber.asFlux()
                    .doFinally(signalType -> {
                        subscribers.remove(subscriber);
                        cleanupAction.run();
                        subscriber.complete();
                    });
        });
    }

    private TaskRealtimeEventResponse buildConnectedEvent(String message) {
        return new TaskRealtimeEventResponse(
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
    }

    private void emitToSubscribers(Set<TaskEventSubscriber> subscribers, TaskRealtimeEventResponse event) {
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }
        for (TaskEventSubscriber subscriber : subscribers) {
            subscriber.emit(event);
        }
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

    @FunctionalInterface
    private interface TaskEventSubscriber {

        void emit(TaskRealtimeEventResponse event);
    }

    private static final class BufferedTaskEventSubscriber implements TaskEventSubscriber {

        private final Sinks.Many<TaskRealtimeEventResponse> sink = Sinks.many().unicast().onBackpressureBuffer();
        private final Object snapshotLock = new Object();
        private final Queue<TaskRealtimeEventResponse> bufferedEvents = new ConcurrentLinkedQueue<>();

        private boolean snapshotCompleted;

        private Flux<TaskRealtimeEventResponse> asFlux() {
            return sink.asFlux();
        }

        @Override
        public void emit(TaskRealtimeEventResponse event) {
            synchronized (snapshotLock) {
                if (snapshotCompleted) {
                    emitDirect(event);
                    return;
                }
                bufferedEvents.add(event);
            }
        }

        private void emitSnapshot(TaskRealtimeEventResponse event) {
            emitDirect(event);
        }

        private void completeSnapshot() {
            List<TaskRealtimeEventResponse> pendingEvents;
            synchronized (snapshotLock) {
                snapshotCompleted = true;
                pendingEvents = new ArrayList<>(bufferedEvents);
                bufferedEvents.clear();
            }
            pendingEvents.forEach(this::emitDirect);
        }

        private void emitDirect(TaskRealtimeEventResponse event) {
            Sinks.EmitResult result = sink.tryEmitNext(event);
            if (result == Sinks.EmitResult.OK
                    || result == Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER
                    || result == Sinks.EmitResult.FAIL_CANCELLED
                    || result == Sinks.EmitResult.FAIL_TERMINATED) {
                return;
            }
            throw new IllegalStateException("任务实时事件写入响应式流失败: " + result);
        }

        private void complete() {
            sink.tryEmitComplete();
        }
    }
}
