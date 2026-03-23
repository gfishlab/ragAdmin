package com.ragadmin.server.statistics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragadmin.server.chat.entity.ChatMessageEntity;
import com.ragadmin.server.chat.entity.ChatSessionEntity;
import com.ragadmin.server.chat.mapper.ChatMessageMapper;
import com.ragadmin.server.chat.mapper.ChatSessionMapper;
import com.ragadmin.server.document.support.EmbeddingModelDescriptor;
import com.ragadmin.server.infra.vector.MilvusVectorStoreClient;
import com.ragadmin.server.knowledge.entity.KnowledgeBaseEntity;
import com.ragadmin.server.knowledge.service.KnowledgeBaseService;
import com.ragadmin.server.model.entity.AiModelEntity;
import com.ragadmin.server.model.service.ModelService;
import com.ragadmin.server.statistics.dto.KnowledgeBaseChatStatisticsResponse;
import com.ragadmin.server.statistics.dto.ModelCallStatisticsResponse;
import com.ragadmin.server.statistics.dto.VectorIndexCollectionStatRow;
import com.ragadmin.server.statistics.dto.VectorIndexOverviewResponse;
import com.ragadmin.server.statistics.mapper.VectorIndexOverviewMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class StatisticsService {

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    private ModelService modelService;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private MilvusVectorStoreClient milvusVectorStoreClient;

    @Autowired
    private VectorIndexOverviewMapper vectorIndexOverviewMapper;

    public List<ModelCallStatisticsResponse> modelCalls() {
        List<ChatMessageEntity> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                .isNotNull(ChatMessageEntity::getModelId));
        if (messages.isEmpty()) {
            return List.of();
        }

        Map<Long, AiModelEntity> modelMap = modelService.findByIds(messages.stream()
                        .map(ChatMessageEntity::getModelId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(AiModelEntity::getId, model -> model));

        return messages.stream()
                .collect(Collectors.groupingBy(ChatMessageEntity::getModelId))
                .entrySet()
                .stream()
                .map(entry -> {
                    AiModelEntity model = modelMap.get(entry.getKey());
                    List<ChatMessageEntity> items = entry.getValue();
                    long callCount = items.size();
                    long totalPromptTokens = items.stream().map(ChatMessageEntity::getPromptTokens).filter(java.util.Objects::nonNull).mapToLong(Integer::longValue).sum();
                    long totalCompletionTokens = items.stream().map(ChatMessageEntity::getCompletionTokens).filter(java.util.Objects::nonNull).mapToLong(Integer::longValue).sum();
                    long averageLatency = Math.round(items.stream().map(ChatMessageEntity::getLatencyMs).filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0D));
                    return new ModelCallStatisticsResponse(
                            entry.getKey(),
                            model == null ? null : model.getModelCode(),
                            model == null ? null : model.getModelName(),
                            callCount,
                            totalPromptTokens,
                            totalCompletionTokens,
                            averageLatency
                    );
                })
                .sorted(Comparator.comparing(ModelCallStatisticsResponse::callCount).reversed())
                .toList();
    }

    public KnowledgeBaseChatStatisticsResponse knowledgeBaseChat(Long kbId) {
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseService.requireById(kbId);
        List<ChatSessionEntity> sessions = chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getKbId, knowledgeBase.getId()));
        if (sessions.isEmpty()) {
            return new KnowledgeBaseChatStatisticsResponse(kbId, 0L, 0L, 0L, 0L, 0L, 0L);
        }

        List<Long> sessionIds = sessions.stream().map(ChatSessionEntity::getId).toList();
        List<ChatMessageEntity> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                .in(ChatMessageEntity::getSessionId, sessionIds));

        long totalPromptTokens = messages.stream().map(ChatMessageEntity::getPromptTokens).filter(java.util.Objects::nonNull).mapToLong(Integer::longValue).sum();
        long totalCompletionTokens = messages.stream().map(ChatMessageEntity::getCompletionTokens).filter(java.util.Objects::nonNull).mapToLong(Integer::longValue).sum();
        long averageLatency = Math.round(messages.stream().map(ChatMessageEntity::getLatencyMs).filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0D));
        long distinctUserCount = sessions.stream().map(ChatSessionEntity::getUserId).distinct().count();

        return new KnowledgeBaseChatStatisticsResponse(
                kbId,
                (long) sessions.size(),
                (long) messages.size(),
                distinctUserCount,
                totalPromptTokens,
                totalCompletionTokens,
                averageLatency
        );
    }

    public List<VectorIndexOverviewResponse> vectorIndexes(String keyword, String status) {
        List<VectorIndexOverviewResponse> overviews = vectorIndexOverviewMapper.selectOverview(keyword, status);
        if (overviews.isEmpty()) {
            return List.of();
        }

        List<Long> kbIds = overviews.stream()
                .map(VectorIndexOverviewResponse::getKbId)
                .filter(Objects::nonNull)
                .toList();
        Map<String, VectorIndexCollectionStatRow> statMap = vectorIndexOverviewMapper.selectVectorStats(kbIds).stream()
                .collect(Collectors.toMap(
                        item -> buildVectorStatKey(item.getKbId(), item.getEmbeddingModelId()),
                        item -> item
                ));

        Map<Long, EmbeddingModelDescriptor> descriptorMap = new HashMap<>();
        for (VectorIndexOverviewResponse item : overviews) {
            if (item.getConfiguredEmbeddingModelId() == null) {
                item.setEmbeddingModelSource("UNSET");
                item.setDocumentCount(defaultLong(item.getDocumentCount()));
                item.setSuccessDocumentCount(defaultLong(item.getSuccessDocumentCount()));
                item.setChunkCount(defaultLong(item.getChunkCount()));
                item.setVectorRefCount(0L);
                item.setMilvusStatus("DOWN");
                item.setMilvusMessage("知识库未绑定向量模型，无法继续校验向量索引。");
                continue;
            }
            try {
                EmbeddingModelDescriptor descriptor = modelService.resolveEmbeddingModelDescriptor(item.getConfiguredEmbeddingModelId());
                descriptorMap.put(item.getKbId(), descriptor);
            } catch (Exception ex) {
                item.setEmbeddingModelSource("CUSTOM");
                item.setDocumentCount(defaultLong(item.getDocumentCount()));
                item.setSuccessDocumentCount(defaultLong(item.getSuccessDocumentCount()));
                item.setChunkCount(defaultLong(item.getChunkCount()));
                item.setVectorRefCount(0L);
                item.setMilvusStatus("DOWN");
                item.setMilvusMessage("向量模型解析失败: " + ex.getMessage());
            }
        }

        Map<Long, AiModelEntity> modelMap = modelService.findByIds(descriptorMap.values().stream()
                        .map(EmbeddingModelDescriptor::modelId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(AiModelEntity::getId, model -> model));

        for (VectorIndexOverviewResponse item : overviews) {
            item.setDocumentCount(defaultLong(item.getDocumentCount()));
            item.setSuccessDocumentCount(defaultLong(item.getSuccessDocumentCount()));
            item.setChunkCount(defaultLong(item.getChunkCount()));
            item.setVectorRefCount(defaultLong(item.getVectorRefCount()));

            EmbeddingModelDescriptor descriptor = descriptorMap.get(item.getKbId());
            if (descriptor == null) {
                continue;
            }

            item.setEffectiveEmbeddingModelId(descriptor.modelId());
            item.setEmbeddingModelSource("CUSTOM");
            item.setEmbeddingModelCode(descriptor.modelCode());
            AiModelEntity model = modelMap.get(descriptor.modelId());
            item.setEmbeddingModelName(model == null ? descriptor.modelCode() : model.getModelName());

            VectorIndexCollectionStatRow stat = statMap.get(buildVectorStatKey(item.getKbId(), descriptor.modelId()));
            if (stat != null) {
                item.setVectorRefCount(defaultLong(stat.getVectorRefCount()));
                item.setCollectionName(stat.getCollectionName());
                item.setEmbeddingDim(stat.getEmbeddingDim());
                item.setLatestVectorizedAt(stat.getLatestVectorizedAt());
            }

            fillMilvusState(item);
        }

        return overviews;
    }

    private void fillMilvusState(VectorIndexOverviewResponse item) {
        if (!StringUtils.hasText(item.getCollectionName()) || defaultLong(item.getVectorRefCount()) == 0L) {
            item.setMilvusStatus("EMPTY");
            if (defaultLong(item.getChunkCount()) > 0) {
                item.setMilvusMessage("已存在切片，但当前向量模型尚未形成有效索引。");
            } else {
                item.setMilvusMessage("当前知识库还没有切片和向量索引。");
            }
            return;
        }

        try {
            MilvusVectorStoreClient.CollectionDescription description = milvusVectorStoreClient.describeCollection(item.getCollectionName());
            if ("DISABLED".equals(description.loadState())) {
                item.setMilvusStatus("UNKNOWN");
                item.setMilvusMessage("Milvus 已禁用，无法校验集合加载状态。");
                return;
            }
            if (item.getEmbeddingDim() == null) {
                item.setEmbeddingDim(description.dimension());
            }
            if ("LoadStateLoaded".equalsIgnoreCase(description.loadState())) {
                item.setMilvusStatus("UP");
                item.setMilvusMessage(buildMilvusLoadedMessage(description));
                return;
            }
            item.setMilvusStatus("NOT_LOADED");
            item.setMilvusMessage(StringUtils.hasText(description.loadState())
                    ? "集合存在，但当前未加载，loadState=" + description.loadState()
                    : "集合存在，但未返回加载状态。");
        } catch (Exception ex) {
            item.setMilvusStatus("DOWN");
            item.setMilvusMessage("Milvus 集合校验失败: " + ex.getMessage());
        }
    }

    private String buildMilvusLoadedMessage(MilvusVectorStoreClient.CollectionDescription description) {
        if (StringUtils.hasText(description.metricType())) {
            return "集合已加载，metricType=" + description.metricType();
        }
        return "集合已加载，可用于向量检索。";
    }

    private String buildVectorStatKey(Long kbId, Long embeddingModelId) {
        return kbId + ":" + embeddingModelId;
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }
}
