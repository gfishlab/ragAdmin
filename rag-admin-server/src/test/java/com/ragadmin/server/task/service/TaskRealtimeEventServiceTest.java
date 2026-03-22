package com.ragadmin.server.task.service;

import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.entity.DocumentParseTaskEntity;
import com.ragadmin.server.document.mapper.DocumentMapper;
import com.ragadmin.server.document.mapper.DocumentParseTaskMapper;
import com.ragadmin.server.task.dto.TaskRealtimeEventResponse;
import com.ragadmin.server.task.mapper.TaskStepRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.Disposable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskRealtimeEventServiceTest {

    @Mock
    private DocumentParseTaskMapper documentParseTaskMapper;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private TaskStepRecordMapper taskStepRecordMapper;

    private TaskRealtimeEventService taskRealtimeEventService;

    @BeforeEach
    void setUp() {
        taskRealtimeEventService = new TaskRealtimeEventService(
                documentParseTaskMapper,
                documentMapper,
                taskStepRecordMapper
        );
    }

    @Test
    void shouldEmitConnectedAndSnapshotEventsWhenSubscribeKnowledgeBase() throws InterruptedException {
        DocumentParseTaskEntity task = new DocumentParseTaskEntity();
        task.setId(11L);
        task.setKbId(21L);
        task.setDocumentId(31L);
        task.setTaskStatus("WAITING");

        DocumentEntity document = new DocumentEntity();
        document.setId(31L);
        document.setDocName("员工手册.pdf");
        document.setParseStatus("PENDING");

        when(documentParseTaskMapper.selectList(any())).thenReturn(List.of(task));
        when(documentMapper.selectBatchIds(any())).thenReturn(List.of(document));
        when(taskStepRecordMapper.selectOne(any())).thenReturn(null);

        List<TaskRealtimeEventResponse> events = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);

        Disposable disposable = taskRealtimeEventService.subscribeKnowledgeBase(21L)
                .take(2)
                .subscribe(event -> {
                    events.add(event);
                    latch.countDown();
                });

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals("CONNECTED", events.get(0).eventType());
        assertEquals("知识库文档解析实时通道已连接", events.get(0).message());
        assertEquals("SNAPSHOT", events.get(1).eventType());
        assertEquals(11L, events.get(1).taskId());
        assertEquals("员工手册.pdf", events.get(1).documentName());

        disposable.dispose();
    }

    @Test
    void shouldEmitRealtimeEventThroughKnowledgeBaseFlux() throws InterruptedException {
        when(documentParseTaskMapper.selectList(any())).thenReturn(List.of());

        List<TaskRealtimeEventResponse> events = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);

        Disposable disposable = taskRealtimeEventService.subscribeKnowledgeBase(21L)
                .take(2)
                .subscribe(event -> {
                    events.add(event);
                    latch.countDown();
                });

        DocumentParseTaskEntity task = new DocumentParseTaskEntity();
        task.setId(11L);
        task.setKbId(21L);
        task.setDocumentId(31L);
        task.setTaskStatus("WAITING");

        DocumentEntity document = new DocumentEntity();
        document.setId(31L);
        document.setKbId(21L);
        document.setDocName("员工手册.pdf");
        document.setParseStatus("PENDING");

        taskRealtimeEventService.publishTaskQueued(task, document);

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals("CONNECTED", events.get(0).eventType());
        assertEquals("TASK_QUEUED", events.get(1).eventType());
        assertEquals("解析任务已进入队列", events.get(1).message());

        disposable.dispose();
    }
}
