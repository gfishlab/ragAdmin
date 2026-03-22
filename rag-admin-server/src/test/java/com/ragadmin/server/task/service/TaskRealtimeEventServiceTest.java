package com.ragadmin.server.task.service;

import com.ragadmin.server.document.mapper.DocumentMapper;
import com.ragadmin.server.document.mapper.DocumentParseTaskMapper;
import com.ragadmin.server.task.dto.TaskRealtimeEventResponse;
import com.ragadmin.server.task.mapper.TaskStepRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void shouldRemoveEmitterWhenAsyncResponseIsNotUsable() {
        Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
        emitters.add(new FailingSseEmitter(new AsyncRequestNotUsableException("Response not usable after response errors.")));

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                taskRealtimeEventService,
                "sendToEmitters",
                emitters,
                buildEvent()
        ));

        assertTrue(emitters.isEmpty());
    }

    @Test
    void shouldRemoveEmitterWhenEmitterAlreadyCompleted() {
        Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
        emitters.add(new FailingSseEmitter(new IllegalStateException("ResponseBodyEmitter has already completed")));

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                taskRealtimeEventService,
                "sendToEmitters",
                emitters,
                buildEvent()
        ));

        assertTrue(emitters.isEmpty());
    }

    private TaskRealtimeEventResponse buildEvent() {
        return new TaskRealtimeEventResponse(
                "TASK_STARTED",
                11L,
                21L,
                31L,
                "员工手册.pdf",
                "RUNNING",
                "PROCESSING",
                "EXTRACT_TEXT",
                "文本抽取",
                20,
                "解析任务已开始执行",
                false,
                LocalDateTime.now()
        );
    }

    private static final class FailingSseEmitter extends SseEmitter {

        private final Exception exception;

        private FailingSseEmitter(Exception exception) {
            this.exception = exception;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (exception instanceof IOException ioException) {
                throw ioException;
            }
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(exception);
        }
    }
}
