package com.ragadmin.server.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragadmin.server.chat.ChatContentTypes;
import com.ragadmin.server.chat.dto.ChatAnswerMetadataResponse;
import com.ragadmin.server.chat.dto.ChatReferenceResponse;
import com.ragadmin.server.chat.dto.ChatResponse;
import com.ragadmin.server.chat.dto.ChatUsageResponse;
import com.ragadmin.server.chat.dto.WebSearchSourceResponse;
import com.ragadmin.server.chat.entity.ChatAnswerReferenceEntity;
import com.ragadmin.server.chat.entity.ChatFeedbackEntity;
import com.ragadmin.server.chat.entity.ChatMessageEntity;
import com.ragadmin.server.chat.entity.ChatSessionEntity;
import com.ragadmin.server.chat.entity.ChatWebSearchSourceEntity;
import com.ragadmin.server.chat.mapper.ChatAnswerReferenceMapper;
import com.ragadmin.server.chat.mapper.ChatFeedbackMapper;
import com.ragadmin.server.chat.mapper.ChatMessageMapper;
import com.ragadmin.server.chat.mapper.ChatWebSearchSourceMapper;
import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.mapper.DocumentMapper;
import com.ragadmin.server.infra.ai.chat.ChatAnswerMetadata;
import com.ragadmin.server.infra.search.WebSearchSnippet;
import com.ragadmin.server.retrieval.service.RetrievalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChatExchangePersistenceService {

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ChatAnswerReferenceMapper chatAnswerReferenceMapper;

    @Autowired
    private ChatFeedbackMapper chatFeedbackMapper;

    @Autowired
    private ChatWebSearchSourceMapper chatWebSearchSourceMapper;

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private RetrievalService retrievalService;

    @Transactional
    public ChatResponse persistExchange(
            ChatSessionEntity session,
            Long userId,
            String question,
            String answer,
            Long modelId,
            Integer promptTokens,
            Integer completionTokens,
            int latencyMs,
            ChatAnswerMetadata answerMetadata,
            RetrievalService.RetrievalResult retrievalResult,
            List<WebSearchSnippet> webSearchSnippets
    ) {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setSessionId(session.getId());
        message.setUserId(userId);
        message.setMessageType("RAG");
        message.setQuestionText(question);
        message.setAnswerText(answer);
        message.setModelId(modelId);
        message.setAnswerConfidence(answerMetadata == null ? null : answerMetadata.confidence());
        message.setHasKnowledgeBaseEvidence(answerMetadata == null ? null : answerMetadata.hasKnowledgeBaseEvidence());
        message.setNeedFollowUp(answerMetadata == null ? null : answerMetadata.needFollowUp());
        message.setPromptTokens(promptTokens);
        message.setCompletionTokens(completionTokens);
        message.setLatencyMs(latencyMs);
        chatMessageMapper.insert(message);

        persistReferences(message.getId(), retrievalResult);
        persistWebSearchSources(message.getId(), webSearchSnippets);
        return buildChatResponse(message.getId(), answer, promptTokens, completionTokens, answerMetadata, retrievalResult, webSearchSnippets);
    }

    /**
     * 重新生成只允许覆盖既有消息，避免同一轮问答在会话里落出重复记录。
     */
    @Transactional
    public ChatResponse replaceExchange(
            ChatMessageEntity message,
            String answer,
            Long modelId,
            Integer promptTokens,
            Integer completionTokens,
            int latencyMs,
            ChatAnswerMetadata answerMetadata,
            RetrievalService.RetrievalResult retrievalResult,
            List<WebSearchSnippet> webSearchSnippets
    ) {
        message.setAnswerText(answer);
        message.setModelId(modelId);
        message.setAnswerConfidence(answerMetadata == null ? null : answerMetadata.confidence());
        message.setHasKnowledgeBaseEvidence(answerMetadata == null ? null : answerMetadata.hasKnowledgeBaseEvidence());
        message.setNeedFollowUp(answerMetadata == null ? null : answerMetadata.needFollowUp());
        message.setPromptTokens(promptTokens);
        message.setCompletionTokens(completionTokens);
        message.setLatencyMs(latencyMs);
        chatMessageMapper.updateById(message);

        chatAnswerReferenceMapper.delete(new LambdaQueryWrapper<ChatAnswerReferenceEntity>()
                .eq(ChatAnswerReferenceEntity::getMessageId, message.getId()));
        chatFeedbackMapper.delete(new LambdaQueryWrapper<ChatFeedbackEntity>()
                .eq(ChatFeedbackEntity::getMessageId, message.getId()));
        chatWebSearchSourceMapper.delete(new LambdaQueryWrapper<ChatWebSearchSourceEntity>()
                .eq(ChatWebSearchSourceEntity::getMessageId, message.getId()));
        persistReferences(message.getId(), retrievalResult);
        persistWebSearchSources(message.getId(), webSearchSnippets);
        return buildChatResponse(message.getId(), answer, promptTokens, completionTokens, answerMetadata, retrievalResult, webSearchSnippets);
    }

    private void persistReferences(Long messageId, RetrievalService.RetrievalResult retrievalResult) {
        for (int i = 0; i < retrievalResult.chunks().size(); i++) {
            var chunk = retrievalResult.chunks().get(i);
            ChatAnswerReferenceEntity ref = new ChatAnswerReferenceEntity();
            ref.setMessageId(messageId);
            ref.setChunkId(chunk.chunk().getId());
            ref.setScore(BigDecimal.valueOf(chunk.score()));
            ref.setRankNo(i + 1);
            chatAnswerReferenceMapper.insert(ref);
        }
    }

    /**
     * 联网来源与知识库引用分表持久化，保证历史回放时能明确区分证据来源。
     */
    private void persistWebSearchSources(Long messageId, List<WebSearchSnippet> webSearchSnippets) {
        if (webSearchSnippets == null || webSearchSnippets.isEmpty()) {
            return;
        }
        for (int i = 0; i < webSearchSnippets.size(); i++) {
            WebSearchSnippet snippet = webSearchSnippets.get(i);
            if (snippet == null) {
                continue;
            }
            ChatWebSearchSourceEntity entity = new ChatWebSearchSourceEntity();
            entity.setMessageId(messageId);
            entity.setTitle(snippet.title());
            entity.setSourceUrl(snippet.url());
            entity.setPublishedAt(snippet.publishedAt() == null ? null : LocalDateTime.ofInstant(snippet.publishedAt(), ZoneOffset.UTC));
            entity.setSnippet(snippet.snippet());
            entity.setRankNo(i + 1);
            chatWebSearchSourceMapper.insert(entity);
        }
    }

    private ChatResponse buildChatResponse(
            Long messageId,
            String answer,
            Integer promptTokens,
            Integer completionTokens,
            ChatAnswerMetadata answerMetadata,
            RetrievalService.RetrievalResult retrievalResult,
            List<WebSearchSnippet> webSearchSnippets
    ) {
        List<Long> documentIds = retrievalResult.chunks().stream()
                .map(item -> item.chunk().getDocumentId())
                .distinct()
                .toList();
        Map<Long, String> documentNameResolver = documentIds.isEmpty()
                ? Map.of()
                : documentMapper.selectBatchIds(documentIds)
                .stream()
                .collect(Collectors.toMap(DocumentEntity::getId, DocumentEntity::getDocName));

        List<ChatReferenceResponse> references = retrievalService.toReferenceResponses(
                retrievalResult.chunks(),
                documentId -> documentNameResolver.get(documentId)
        );

        return new ChatResponse(
                messageId,
                answer,
                ChatContentTypes.MARKDOWN,
                references,
                toWebSearchSourceResponses(webSearchSnippets),
                new ChatUsageResponse(promptTokens, completionTokens),
                toMetadataResponse(answerMetadata)
        );
    }

    private List<WebSearchSourceResponse> toWebSearchSourceResponses(List<WebSearchSnippet> webSearchSnippets) {
        if (webSearchSnippets == null || webSearchSnippets.isEmpty()) {
            return List.of();
        }
        return webSearchSnippets.stream()
                .filter(java.util.Objects::nonNull)
                .map(item -> new WebSearchSourceResponse(
                        item.title(),
                        item.url(),
                        item.publishedAt(),
                        item.snippet()
                ))
                .toList();
    }

    private ChatAnswerMetadataResponse toMetadataResponse(ChatAnswerMetadata answerMetadata) {
        if (answerMetadata == null) {
            return null;
        }
        return new ChatAnswerMetadataResponse(
                answerMetadata.confidence(),
                answerMetadata.hasKnowledgeBaseEvidence(),
                answerMetadata.needFollowUp()
        );
    }
}
