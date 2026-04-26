package com.ragadmin.server.document.parser;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragadmin.server.document.entity.ChunkEntity;
import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.entity.DocumentParseTaskEntity;
import com.ragadmin.server.document.entity.DocumentVersionEntity;
import com.ragadmin.server.document.mapper.ChunkMapper;
import com.ragadmin.server.document.mapper.DocumentMapper;
import com.ragadmin.server.document.mapper.DocumentParseTaskMapper;
import com.ragadmin.server.document.mapper.DocumentVersionMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragadmin.server.task.entity.TaskStepRecordEntity;
import com.ragadmin.server.task.mapper.TaskStepRecordMapper;
import com.ragadmin.server.task.service.TaskRealtimeEventService;
import com.ragadmin.server.document.support.ChunkVectorizationService;
import com.ragadmin.server.document.support.ChunkSearchSyncService;
import com.ragadmin.server.document.support.DocumentVectorizationProperties;
import com.ragadmin.server.document.support.DocumentVectorizationStrategyResolver;
import com.ragadmin.server.infra.elasticsearch.ElasticsearchClient;
import com.ragadmin.server.knowledge.entity.KnowledgeBaseEntity;
import com.ragadmin.server.knowledge.service.KnowledgeBaseService;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static com.ragadmin.server.common.config.AsyncExecutionConfiguration.IO_VIRTUAL_TASK_EXECUTOR;

@Component
public class DocumentParseProcessor {

    private static final Logger log = LoggerFactory.getLogger(DocumentParseProcessor.class);
    private static final String STALE_TASK_MESSAGE = "任务执行中断，系统已自动标记失败，请重试";

    private final DocumentParseTaskMapper documentParseTaskMapper;
    private final DocumentMapper documentMapper;
    private final DocumentVersionMapper documentVersionMapper;
    private final ChunkMapper chunkMapper;
    private final DocumentContentExtractor documentContentExtractor;
    private final DocumentCleaner documentCleaner;
    private final TaskStepRecordMapper taskStepRecordMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ChunkVectorizationService chunkVectorizationService;
    private final ChunkSearchSyncService chunkSearchSyncService;
    private final ElasticsearchClient elasticsearchClient;
    private final DocumentVectorizationStrategyResolver strategyResolver;
    private final ChunkProperties chunkProperties;
    private final DocumentChunkStrategyResolver chunkStrategyResolver;
    private final DocumentSignalAnalyzer signalAnalyzer;
    private final TaskRealtimeEventService taskRealtimeEventService;
    private final DocumentParseProperties documentParseProperties;
    private final ExecutorService documentParseExecutor;
    private final TransactionTemplate transactionTemplate;
    private final Set<Long> inFlightTaskIds = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DocumentParseProcessor(
            DocumentParseTaskMapper documentParseTaskMapper,
            DocumentMapper documentMapper,
            DocumentVersionMapper documentVersionMapper,
            ChunkMapper chunkMapper,
            DocumentContentExtractor documentContentExtractor,
            DocumentCleaner documentCleaner,
            TaskStepRecordMapper taskStepRecordMapper,
            KnowledgeBaseService knowledgeBaseService,
            ChunkVectorizationService chunkVectorizationService,
            ChunkSearchSyncService chunkSearchSyncService,
            ElasticsearchClient elasticsearchClient,
            DocumentVectorizationStrategyResolver strategyResolver,
            ChunkProperties chunkProperties,
            DocumentChunkStrategyResolver chunkStrategyResolver,
            DocumentSignalAnalyzer signalAnalyzer,
            TaskRealtimeEventService taskRealtimeEventService,
            DocumentParseProperties documentParseProperties,
            @Qualifier(IO_VIRTUAL_TASK_EXECUTOR) ExecutorService documentParseExecutor,
            PlatformTransactionManager transactionManager
    ) {
        this.documentParseTaskMapper = documentParseTaskMapper;
        this.documentMapper = documentMapper;
        this.documentVersionMapper = documentVersionMapper;
        this.chunkMapper = chunkMapper;
        this.documentContentExtractor = documentContentExtractor;
        this.documentCleaner = documentCleaner;
        this.taskStepRecordMapper = taskStepRecordMapper;
        this.knowledgeBaseService = knowledgeBaseService;
        this.chunkVectorizationService = chunkVectorizationService;
        this.chunkSearchSyncService = chunkSearchSyncService;
        this.elasticsearchClient = elasticsearchClient;
        this.strategyResolver = strategyResolver;
        this.chunkProperties = chunkProperties;
        this.chunkStrategyResolver = chunkStrategyResolver;
        this.signalAnalyzer = signalAnalyzer;
        this.taskRealtimeEventService = taskRealtimeEventService;
        this.documentParseProperties = documentParseProperties;
        this.documentParseExecutor = documentParseExecutor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void processWaitingTasks() {
        recoverStaleRunningTasks();

        int maxConcurrency = Math.max(1, documentParseProperties.getMaxConcurrency());
        int availableSlots = Math.max(0, maxConcurrency - inFlightTaskIds.size());
        if (availableSlots <= 0) {
            return;
        }
        int dispatchLimit = Math.min(availableSlots, Math.max(1, documentParseProperties.getDispatchBatchSize()));
        List<DocumentParseTaskEntity> tasks = documentParseTaskMapper.selectList(new LambdaQueryWrapper<DocumentParseTaskEntity>()
                .eq(DocumentParseTaskEntity::getTaskStatus, "WAITING")
                .orderByAsc(DocumentParseTaskEntity::getId)
                .last("LIMIT " + dispatchLimit));
        for (DocumentParseTaskEntity task : tasks) {
            dispatchTask(task.getId());
        }
    }

    private void dispatchTask(Long taskId) {
        if (!inFlightTaskIds.add(taskId)) {
            return;
        }
        try {
            documentParseExecutor.submit(() -> {
                try {
                    processSingleTask(taskId);
                } finally {
                    inFlightTaskIds.remove(taskId);
                }
            });
        } catch (RejectedExecutionException ex) {
            inFlightTaskIds.remove(taskId);
            log.warn("解析任务分发失败，taskId={}, reason={}", taskId, ex.getMessage());
        }
    }

    public void recoverStaleRunningTasks() {
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(documentParseProperties.getStaleRunningTimeoutMinutes());
        List<DocumentParseTaskEntity> staleTasks = documentParseTaskMapper.selectList(new LambdaQueryWrapper<DocumentParseTaskEntity>()
                .eq(DocumentParseTaskEntity::getTaskStatus, "RUNNING")
                .lt(DocumentParseTaskEntity::getStartedAt, staleBefore)
                .orderByAsc(DocumentParseTaskEntity::getId)
                .last("LIMIT " + documentParseProperties.getStaleRecoveryBatchSize()));
        for (DocumentParseTaskEntity staleTask : staleTasks) {
            markFailed(staleTask.getId(), new IllegalStateException(STALE_TASK_MESSAGE));
            log.warn("检测到超时未完成的解析任务，已自动标记失败，taskId={}, startedAt={}",
                    staleTask.getId(), staleTask.getStartedAt());
        }
    }

    public void processSingleTask(Long taskId) {
        ProcessingContext context;
        try {
            context = markRunning(taskId);
        } catch (IllegalStateException ex) {
            // 调度轮询与多实例并发场景下，任务可能已被其他工作线程抢占，这里按已处理跳过，避免误标记失败。
            log.info("解析任务跳过执行，taskId={}, reason={}", taskId, ex.getMessage());
            return;
        }

        try {
            taskRealtimeEventService.publishTaskStarted(context.task(), context.document());
            KnowledgeBaseEntity knowledgeBase = knowledgeBaseService.requireById(context.document().getKbId());
            DocumentVectorizationProperties.StrategyProperties strategy =
                    strategyResolver.resolveByEmbeddingModelId(knowledgeBase.getEmbeddingModelId()).strategy();

            TaskStepRecordEntity extractStep = startStep(context.task().getId(), "EXTRACT_TEXT", "文本抽取");
            taskRealtimeEventService.publishStepStarted(context.task(), context.document(), extractStep.getStepCode(), extractStep.getStepName());
            ParsedContent parsedContent = parseContent(context.document(), context.version(), strategy);
            attachExtractStepDetail(extractStep, parsedContent);
            logParsedContent(context, parsedContent);
            completeStep(extractStep);
            taskRealtimeEventService.publishStepCompleted(context.task(), context.document(), extractStep.getStepCode(), extractStep.getStepName());

            TaskStepRecordEntity chunkStep = startStep(context.task().getId(), "PERSIST_CHUNKS", "切片入库");
            taskRealtimeEventService.publishStepStarted(context.task(), context.document(), chunkStep.getStepCode(), chunkStep.getStepName());
            List<ChunkEntity> chunks = persistChunks(context, parsedContent.chunks());
            completeStep(chunkStep);
            taskRealtimeEventService.publishStepCompleted(context.task(), context.document(), chunkStep.getStepCode(), chunkStep.getStepName());

            TaskStepRecordEntity embeddingStep = startStep(context.task().getId(), "GENERATE_EMBEDDING", "生成向量");
            taskRealtimeEventService.publishStepStarted(context.task(), context.document(), embeddingStep.getStepCode(), embeddingStep.getStepName());
            chunkVectorizationService.vectorize(knowledgeBase, chunks);
            completeStep(embeddingStep);
            taskRealtimeEventService.publishStepCompleted(context.task(), context.document(), embeddingStep.getStepCode(), embeddingStep.getStepName());

            TaskStepRecordEntity searchSyncStep = startStep(context.task().getId(), "SYNC_SEARCH_ENGINE", "同步检索引擎");
            taskRealtimeEventService.publishStepStarted(context.task(), context.document(), searchSyncStep.getStepCode(), searchSyncStep.getStepName());
            chunkSearchSyncService.syncChunks(context.document().getKbId(), chunks);
            completeStep(searchSyncStep);
            taskRealtimeEventService.publishStepCompleted(context.task(), context.document(), searchSyncStep.getStepCode(), searchSyncStep.getStepName());

            markSuccess(context);
            log.info("文档解析任务执行成功，taskId={}, documentId={}, chunkCount={}",
                    context.task().getId(), context.document().getId(), parsedContent.chunks().size());
        } catch (Exception ex) {
            markFailed(taskId, ex);
            log.error("文档解析任务执行失败，taskId={}", taskId, ex);
        }
    }

    protected ProcessingContext markRunning(Long taskId) {
        return transactionTemplate.execute(status -> {
            DocumentParseTaskEntity task = documentParseTaskMapper.selectById(taskId);
            if (task == null || !"WAITING".equals(task.getTaskStatus())) {
                throw new IllegalStateException("任务不存在或状态已变更");
            }
            DocumentEntity document = documentMapper.selectById(task.getDocumentId());
            DocumentVersionEntity version = documentVersionMapper.selectById(task.getDocumentVersionId());
            if (document == null || version == null) {
                throw new IllegalStateException("文档或文档版本不存在");
            }

            task.setTaskStatus("RUNNING");
            task.setStartedAt(LocalDateTime.now());
            task.setErrorMessage(null);
            documentParseTaskMapper.updateById(task);

            document.setParseStatus("PROCESSING");
            documentMapper.updateById(document);
            version.setParseStatus("PROCESSING");
            version.setParseStartedAt(LocalDateTime.now());
            version.setParseFinishedAt(null);
            documentVersionMapper.updateById(version);

            return new ProcessingContext(task, document, version);
        });
    }

    protected TaskStepRecordEntity startStep(Long taskId, String stepCode, String stepName) {
        return transactionTemplate.execute(status -> {
            TaskStepRecordEntity step = new TaskStepRecordEntity();
            step.setTaskId(taskId);
            step.setStepCode(stepCode);
            step.setStepName(stepName);
            step.setStepStatus("RUNNING");
            step.setStartedAt(LocalDateTime.now());
            taskStepRecordMapper.insert(step);
            return step;
        });
    }

    protected void completeStep(TaskStepRecordEntity step) {
        transactionTemplate.executeWithoutResult(status -> {
            step.setStepStatus("SUCCESS");
            step.setFinishedAt(LocalDateTime.now());
            step.setErrorMessage(null);
            taskStepRecordMapper.updateById(step);
        });
    }

    protected void attachExtractStepDetail(TaskStepRecordEntity step, ParsedContent parsedContent) {
        transactionTemplate.executeWithoutResult(status -> {
            List<String> readerTypes = parsedContent.sourceDocuments().stream()
                    .map(document -> String.valueOf(document.getMetadata().get("readerType")))
                    .distinct()
                    .toList();
            List<String> parseModes = parsedContent.sourceDocuments().stream()
                    .map(document -> String.valueOf(document.getMetadata().get("parseMode")))
                    .distinct()
                    .toList();
            List<String> mineruTaskIds = parsedContent.sourceDocuments().stream()
                    .map(document -> document.getMetadata().get("mineruTaskId"))
                    .filter(value -> value != null)
                    .map(String::valueOf)
                    .distinct()
                    .toList();
            List<String> mineruResultSources = parsedContent.sourceDocuments().stream()
                    .map(document -> document.getMetadata().get("mineruResultSource"))
                    .filter(value -> value != null)
                    .map(String::valueOf)
                    .distinct()
                    .toList();
            Map<String, Object> detail = Map.of(
                    "readerTypes", readerTypes,
                    "parseModes", parseModes,
                    "mineruTaskIds", mineruTaskIds,
                    "mineruResultSources", mineruResultSources,
                    "sourceDocumentCount", parsedContent.sourceDocuments().size(),
                    "chunkDraftCount", parsedContent.chunks().size()
            );
            taskStepRecordMapper.updateDetailJson(step.getId(), toMetadataJson(detail));
        });
    }

    protected List<ChunkEntity> persistChunks(ProcessingContext context, List<ChunkDraft> chunks) {
        return transactionTemplate.execute(status -> {
            List<ChunkEntity> existingChunks = chunkMapper.selectList(new LambdaQueryWrapper<ChunkEntity>()
                    .eq(ChunkEntity::getDocumentVersionId, context.version().getId())
                    .orderByAsc(ChunkEntity::getChunkNo));
            chunkVectorizationService.deleteRefsByChunkIds(existingChunks.stream().map(ChunkEntity::getId).toList());
            chunkMapper.delete(new LambdaQueryWrapper<ChunkEntity>()
                    .eq(ChunkEntity::getDocumentVersionId, context.version().getId()));

            int chunkNo = 1;
            List<ChunkEntity> persistedChunks = new ArrayList<>();
            for (ChunkDraft chunk : chunks) {
                ChunkEntity entity = new ChunkEntity();
                entity.setKbId(context.document().getKbId());
                entity.setDocumentId(context.document().getId());
                entity.setDocumentVersionId(context.version().getId());
                entity.setChunkNo(chunkNo++);
                entity.setChunkText(chunk.text());
                entity.setTokenCount(estimateTokenCount(chunk.text()));
                entity.setCharCount(chunk.text().length());
                entity.setMetadataJson(toMetadataJson(chunk.metadata()));
                entity.setParentChunkId(chunk.parentChunkId());
                entity.setChunkStrategy(extractChunkStrategy(chunk.metadata()));
                entity.setEnabled(Boolean.TRUE);
                chunkMapper.insertWithJsonb(entity);
                persistedChunks.add(entity);
            }
            return persistedChunks;
        });
    }

    protected void markSuccess(ProcessingContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();
            DocumentParseTaskEntity task = context.task();
            DocumentEntity document = context.document();
            DocumentVersionEntity version = context.version();

            task.setTaskStatus("SUCCESS");
            task.setFinishedAt(now);
            task.setErrorMessage(null);
            documentParseTaskMapper.updateById(task);

            document.setParseStatus("SUCCESS");
            documentMapper.updateById(document);
            version.setParseStatus("SUCCESS");
            version.setParseFinishedAt(now);
            documentVersionMapper.updateById(version);
        });
        taskRealtimeEventService.publishTaskSucceeded(context.task(), context.document());
    }

    protected void markFailed(Long taskId, Exception ex) {
        TaskStepRecordEntity currentStep = taskStepRecordMapper.selectOne(new LambdaQueryWrapper<TaskStepRecordEntity>()
                .eq(TaskStepRecordEntity::getTaskId, taskId)
                .eq(TaskStepRecordEntity::getStepStatus, "RUNNING")
                .orderByDesc(TaskStepRecordEntity::getId)
                .last("LIMIT 1"));
        final String currentStepCode = currentStep == null ? null : currentStep.getStepCode();
        final String currentStepName = currentStep == null ? null : currentStep.getStepName();

        transactionTemplate.executeWithoutResult(status -> {
            DocumentParseTaskEntity task = documentParseTaskMapper.selectById(taskId);
            if (task == null) {
                return;
            }
            DocumentEntity document = documentMapper.selectById(task.getDocumentId());
            DocumentVersionEntity version = documentVersionMapper.selectById(task.getDocumentVersionId());
            LocalDateTime now = LocalDateTime.now();

            task.setTaskStatus("FAILED");
            task.setFinishedAt(now);
            task.setErrorMessage(buildErrorMessage(ex));
            documentParseTaskMapper.updateById(task);

            if (document != null) {
                document.setParseStatus("FAILED");
                documentMapper.updateById(document);
            }
            if (version != null) {
                version.setParseStatus("FAILED");
                version.setParseFinishedAt(now);
                documentVersionMapper.updateById(version);
            }

            TaskStepRecordEntity runningStep = taskStepRecordMapper.selectOne(new LambdaQueryWrapper<TaskStepRecordEntity>()
                    .eq(TaskStepRecordEntity::getTaskId, taskId)
                    .eq(TaskStepRecordEntity::getStepStatus, "RUNNING")
                    .orderByDesc(TaskStepRecordEntity::getId)
                    .last("LIMIT 1"));
            if (runningStep != null) {
                runningStep.setStepStatus("FAILED");
                runningStep.setFinishedAt(now);
                runningStep.setErrorMessage(buildErrorMessage(ex));
                taskStepRecordMapper.updateById(runningStep);
            }
        });
        DocumentParseTaskEntity task = documentParseTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        DocumentEntity document = documentMapper.selectById(task.getDocumentId());
        taskRealtimeEventService.publishTaskFailed(task, document, currentStepCode, currentStepName);
    }

    private ParsedContent parseContent(
            DocumentEntity document,
            DocumentVersionEntity version,
            DocumentVectorizationProperties.StrategyProperties strategy
    ) throws Exception {
        List<Document> rawDocuments = documentContentExtractor.extract(document, version);
        DocumentCleanContext cleanContext = new DocumentCleanContext(document, DocumentCleanPolicy.defaultPolicy());
        List<Document> cleanedDocuments = documentCleaner.clean(rawDocuments, cleanContext);

        DocumentSignals signals = signalAnalyzer.analyze(cleanedDocuments, cleanContext);
        String contentType = signals.inferContentType();
        ChunkStrategyProperties chunkProps = chunkProperties.resolve(contentType);
        String parseMode = extractParseMode(cleanedDocuments);
        ChunkContext chunkContext = new ChunkContext(document, signals, chunkProps, parseMode, contentType);

        DocumentChunkStrategy chunkStrategy = chunkStrategyResolver.resolve(chunkContext);
        List<ChunkDraft> chunks = chunkStrategy.chunk(cleanedDocuments, chunkContext);

        return new ParsedContent(cleanedDocuments, chunks);
    }

    private void logParsedContent(ProcessingContext context, ParsedContent parsedContent) {
        List<String> readerTypes = parsedContent.sourceDocuments().stream()
                .map(document -> String.valueOf(document.getMetadata().get("readerType")))
                .distinct()
                .toList();
        List<String> parseModes = parsedContent.sourceDocuments().stream()
                .map(document -> String.valueOf(document.getMetadata().get("parseMode")))
                .distinct()
                .toList();
        List<String> mineruTaskIds = parsedContent.sourceDocuments().stream()
                .map(document -> document.getMetadata().get("mineruTaskId"))
                .filter(value -> value != null)
                .map(String::valueOf)
                .distinct()
                .toList();
        List<String> mineruResultSources = parsedContent.sourceDocuments().stream()
                .map(document -> document.getMetadata().get("mineruResultSource"))
                .filter(value -> value != null)
                .map(String::valueOf)
                .distinct()
                .toList();
        log.info("文档文本抽取完成，taskId={}, documentId={}, sourceDocumentCount={}, chunkDraftCount={}, readerTypes={}, parseModes={}, mineruTaskIds={}, mineruResultSources={}",
                context.task().getId(),
                context.document().getId(),
                parsedContent.sourceDocuments().size(),
                parsedContent.chunks().size(),
                readerTypes,
                parseModes,
                mineruTaskIds,
                mineruResultSources);
    }


    private String toMetadataJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("切片 metadata 序列化失败", ex);
        }
    }

    private int estimateTokenCount(String text) {
        return Math.max(1, text.length() / documentParseProperties.getTokenEstimationDivisor());
    }

    private String extractParseMode(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return "TEXT";
        }
        Object mode = documents.getFirst().getMetadata().get("parseMode");
        return mode != null ? String.valueOf(mode) : "TEXT";
    }

    private String buildErrorMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "解析失败";
        }
        int maxLen = documentParseProperties.getErrorMessageMaxLength();
        return message.length() > maxLen ? message.substring(0, maxLen) : message;
    }

    private String extractChunkStrategy(Map<String, Object> metadata) {
        if (metadata == null) return null;
        Object strategy = metadata.get("chunkStrategy");
        return strategy != null ? String.valueOf(strategy) : null;
    }

    private record ProcessingContext(
            DocumentParseTaskEntity task,
            DocumentEntity document,
            DocumentVersionEntity version
    ) {
    }

    private record ParsedContent(List<Document> sourceDocuments, List<ChunkDraft> chunks) {
    }
}
