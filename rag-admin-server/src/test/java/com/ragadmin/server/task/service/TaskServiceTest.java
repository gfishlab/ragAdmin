package com.ragadmin.server.task.service;

import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.entity.DocumentParseTaskEntity;
import com.ragadmin.server.document.mapper.DocumentMapper;
import com.ragadmin.server.document.mapper.DocumentParseTaskMapper;
import com.ragadmin.server.document.service.DocumentService;
import com.ragadmin.server.task.dto.TaskDetailResponse;
import com.ragadmin.server.task.dto.TaskSummaryResponse;
import com.ragadmin.server.task.entity.TaskRetryRecordEntity;
import com.ragadmin.server.task.mapper.TaskRetryRecordMapper;
import com.ragadmin.server.task.mapper.TaskStepRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private DocumentParseTaskMapper documentParseTaskMapper;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private DocumentService documentService;

    @Mock
    private TaskStepRecordMapper taskStepRecordMapper;

    @Mock
    private TaskRetryRecordMapper taskRetryRecordMapper;

    @InjectMocks
    private TaskService taskService;

    @Test
    void shouldRejectRetryWhenTaskSucceeded() {
        DocumentParseTaskEntity task = new DocumentParseTaskEntity();
        task.setId(101L);
        task.setTaskStatus("SUCCESS");

        when(documentParseTaskMapper.selectById(101L)).thenReturn(task);

        BusinessException exception = assertThrows(BusinessException.class, () -> taskService.retry(101L));

        assertEquals("TASK_RETRY_NOT_ALLOWED", exception.getCode());
        assertTrue(exception.getMessage().contains("仅失败或已取消的任务允许重试"));
        verify(documentService, never()).submitParseTask(any(Long.class), any(Long.class), any(Integer.class));
    }

    @Test
    void shouldRetryUsingOriginalDocumentVersion() {
        DocumentParseTaskEntity failedTask = new DocumentParseTaskEntity();
        failedTask.setId(201L);
        failedTask.setKbId(11L);
        failedTask.setDocumentId(301L);
        failedTask.setDocumentVersionId(401L);
        failedTask.setTaskStatus("FAILED");
        failedTask.setRetryCount(1);

        DocumentParseTaskEntity retriedTask = new DocumentParseTaskEntity();
        retriedTask.setId(202L);
        retriedTask.setKbId(11L);
        retriedTask.setDocumentId(301L);
        retriedTask.setDocumentVersionId(401L);
        retriedTask.setTaskStatus("WAITING");
        retriedTask.setRetryCount(2);

        DocumentEntity document = new DocumentEntity();
        document.setId(301L);
        document.setDocName("员工手册.pdf");
        document.setParseStatus("PENDING");

        when(documentParseTaskMapper.selectById(201L)).thenReturn(failedTask);
        when(documentService.submitParseTask(301L, 401L, 2)).thenReturn(retriedTask);
        when(documentMapper.selectById(301L)).thenReturn(document);
        when(taskStepRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(taskRetryRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        TaskDetailResponse response = taskService.retry(201L);

        verify(documentService).submitParseTask(301L, 401L, 2);

        ArgumentCaptor<TaskRetryRecordEntity> captor = ArgumentCaptor.forClass(TaskRetryRecordEntity.class);
        verify(taskRetryRecordMapper).insert(captor.capture());
        TaskRetryRecordEntity inserted = captor.getValue();
        assertEquals(202L, inserted.getTaskId());
        assertEquals(2, inserted.getRetryNo());
        assertEquals("SUBMITTED", inserted.getRetryResult());

        assertEquals(202L, response.taskId());
        assertEquals(401L, response.documentVersionId());
        assertEquals(2, response.retryCount());
    }

    @Test
    void shouldReturnTaskSummaryCounts() {
        when(documentParseTaskMapper.selectCount(any())).thenReturn(10L, 2L, 1L, 5L, 1L, 1L);

        TaskSummaryResponse response = taskService.summary("DOCUMENT_PARSE", null);

        assertEquals(10L, response.total());
        assertEquals(2L, response.waiting());
        assertEquals(1L, response.running());
        assertEquals(5L, response.success());
        assertEquals(1L, response.failed());
        assertEquals(1L, response.canceled());
    }
}
