package com.ragadmin.server.retrieval.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragadmin.server.document.entity.ChunkEntity;
import com.ragadmin.server.infra.ai.chat.ChatCompletionResult;
import com.ragadmin.server.infra.ai.chat.ChatPromptMessage;
import com.ragadmin.server.infra.ai.chat.ConversationChatClient;
import com.ragadmin.server.infra.ai.chat.PromptTemplateService;
import com.ragadmin.server.model.service.ModelService;
import com.ragadmin.server.retrieval.config.ConflictDetectionProperties;
import com.ragadmin.server.retrieval.model.ConflictDetectionResult;
import com.ragadmin.server.retrieval.model.ConflictGroup;
import com.ragadmin.server.retrieval.model.ConflictType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ConflictDetectionService {

    private static final Logger log = LoggerFactory.getLogger(ConflictDetectionService.class);
    private static final int CHUNK_TEXT_MAX_CHARS = 500;

    @Autowired
    private ConflictDetectionProperties properties;

    @Autowired
    private ConversationChatClient conversationChatClient;

    @Autowired
    private PromptTemplateService promptTemplateService;

    @Autowired
    private ModelService modelService;

    @Autowired
    private ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Value("classpath:prompts/ai/retrieval/conflict-detection-system.st")
    private Resource nliSystemPromptResource;

    @org.springframework.beans.factory.annotation.Value("classpath:prompts/ai/retrieval/conflict-detection-user.st")
    private Resource nliUserPromptResource;

    @org.springframework.beans.factory.annotation.Value("classpath:prompts/ai/retrieval/conflict-ie-system.st")
    private Resource ieSystemPromptResource;

    @org.springframework.beans.factory.annotation.Value("classpath:prompts/ai/retrieval/conflict-ie-user.st")
    private Resource ieUserPromptResource;

    public ConflictDetectionResult detect(String query, List<RetrievalService.RetrievedChunk> chunks) {
        if (!properties.isEnabled() || !properties.isLlmNliMode() || chunks.size() <= 1) {
            return new ConflictDetectionResult(List.of(), chunks);
        }

        try {
            // Step 1: 元数据预筛选 — 同文档多版本检测（不依赖模型）
            List<ConflictGroup> conflicts = new ArrayList<>(detectVersionConflicts(chunks));

            // Step 2: 信息抽取 + NLI 矛盾检测（需要模型）
            var chatModel = modelService.findDefaultChatModelDescriptor();
            if (chatModel != null) {
                List<ConflictGroup> semanticConflicts = detectSemanticConflicts(chunks, conflicts);
                conflicts.addAll(semanticConflicts);
            } else {
                log.debug("无可用聊天模型，跳过语义矛盾检测");
            }

            // Step 3: 冲突消解
            List<RetrievalService.RetrievedChunk> resolved = resolveConflicts(chunks, conflicts);

            log.info("冲突检测完成: chunks={}, conflicts={}, resolved={}", chunks.size(), conflicts.size(), resolved.size());
            return new ConflictDetectionResult(conflicts, resolved);
        } catch (Exception e) {
            log.warn("冲突检测失败，返回原始结果: {}", e.getMessage());
            return new ConflictDetectionResult(List.of(), chunks);
        }
    }

    private List<ConflictGroup> detectVersionConflicts(List<RetrievalService.RetrievedChunk> chunks) {
        List<ConflictGroup> conflicts = new ArrayList<>();

        Map<Long, List<RetrievalService.RetrievedChunk>> byDocument = new HashMap<>();
        for (var chunk : chunks) {
            Long docId = chunk.chunk().getDocumentId();
            if (docId != null) {
                byDocument.computeIfAbsent(docId, k -> new ArrayList<>()).add(chunk);
            }
        }

        for (var entry : byDocument.entrySet()) {
            List<RetrievalService.RetrievedChunk> docChunks = entry.getValue();
            if (docChunks.size() < 2) continue;

            Set<Long> versionIds = new HashSet<>();
            for (var chunk : docChunks) {
                Long versionId = chunk.chunk().getDocumentVersionId();
                if (versionId != null) {
                    versionIds.add(versionId);
                }
            }

            if (versionIds.size() > 1) {
                List<Long> chunkIds = docChunks.stream()
                        .map(c -> c.chunk().getId())
                        .toList();

                Long preferredId = resolveByVersion(docChunks);
                String reason = preferredId != null ? "选择更高版本的文档片段" : null;

                conflicts.add(new ConflictGroup(
                        UUID.randomUUID().toString(),
                        ConflictType.VERSION,
                        1.0,
                        preferredId,
                        reason,
                        chunkIds
                ));

                log.debug("检测到版本冲突: documentId={}, versionCount={}, chunks={}",
                        entry.getKey(), versionIds.size(), chunkIds.size());
            }
        }

        return conflicts;
    }

    private List<ConflictGroup> detectSemanticConflicts(
            List<RetrievalService.RetrievedChunk> chunks,
            List<ConflictGroup> existingConflicts) {

        List<ConflictGroup> conflicts = new ArrayList<>();

        Set<Long> alreadyInConflict = new HashSet<>();
        for (var group : existingConflicts) {
            alreadyInConflict.addAll(group.getChunkIds());
        }

        List<RetrievalService.RetrievedChunk> candidates = chunks.stream()
                .filter(c -> !alreadyInConflict.contains(c.chunk().getId()))
                .toList();

        if (candidates.size() < 2) {
            return conflicts;
        }

        try {
            var chatModel = modelService.findDefaultChatModelDescriptor();
            if (chatModel == null) return conflicts;

            Map<Long, ExtractedInfo> infoMap = extractInfo(candidates, chatModel);

            Map<String, List<RetrievalService.RetrievedChunk>> bySubject = new HashMap<>();
            for (var candidate : candidates) {
                ExtractedInfo info = infoMap.get(candidate.chunk().getId());
                if (info != null && StringUtils.hasText(info.subject)) {
                    String key = info.subject + "||" + info.attribute;
                    bySubject.computeIfAbsent(key, k -> new ArrayList<>()).add(candidate);
                }
            }

            int pairCount = 0;
            int maxPairs = properties.getLlmNli().getMaxPairs();

            for (var entry : bySubject.entrySet()) {
                List<RetrievalService.RetrievedChunk> group = entry.getValue();
                if (group.size() < 2) continue;

                for (int i = 0; i < group.size() && pairCount < maxPairs; i++) {
                    for (int j = i + 1; j < group.size() && pairCount < maxPairs; j++) {
                        NliResult nli = checkNli(group.get(i), group.get(j), chatModel);
                        pairCount++;

                        if ("contradiction".equals(nli.relation)
                                && nli.confidence >= properties.getLlmNli().getConfidenceThreshold()) {

                            List<Long> chunkIds = List.of(
                                    group.get(i).chunk().getId(),
                                    group.get(j).chunk().getId()
                            );

                            Long preferredId = resolveByMetadata(group.get(i), group.get(j));

                            conflicts.add(new ConflictGroup(
                                    UUID.randomUUID().toString(),
                                    ConflictType.FACT,
                                    nli.confidence,
                                    preferredId,
                                    nli.reason,
                                    chunkIds
                            ));

                            log.debug("检测到语义矛盾: chunkA={}, chunkB={}, confidence={}, reason={}",
                                    group.get(i).chunk().getId(), group.get(j).chunk().getId(),
                                    nli.confidence, nli.reason);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("语义矛盾检测失败: {}", e.getMessage());
        }

        return conflicts;
    }

    private Map<Long, ExtractedInfo> extractInfo(
            List<RetrievalService.RetrievedChunk> chunks,
            ModelService.ChatModelDescriptor chatModel) throws Exception {

        Map<Long, ExtractedInfo> result = new HashMap<>();
        String systemPrompt = promptTemplateService.load(ieSystemPromptResource);
        String userTemplate = promptTemplateService.load(ieUserPromptResource);

        for (var chunk : chunks) {
            try {
                String text = truncate(chunk.chunk().getChunkText(), CHUNK_TEXT_MAX_CHARS);
                String userPrompt = userTemplate.replace("{chunk_text}", text);

                List<ChatPromptMessage> messages = List.of(
                        new ChatPromptMessage("system", systemPrompt),
                        new ChatPromptMessage("user", userPrompt)
                );

                ChatCompletionResult completion = conversationChatClient.chat(
                        chatModel.providerCode(), chatModel.modelCode(), messages);

                ExtractedInfo info = parseExtractedInfo(completion.content());
                if (info != null) {
                    result.put(chunk.chunk().getId(), info);
                }
            } catch (Exception e) {
                log.debug("信息抽取失败 chunk={}: {}", chunk.chunk().getId(), e.getMessage());
            }
        }

        return result;
    }

    private NliResult checkNli(
            RetrievalService.RetrievedChunk chunkA,
            RetrievalService.RetrievedChunk chunkB,
            ModelService.ChatModelDescriptor chatModel) throws Exception {

        String systemPrompt = promptTemplateService.load(nliSystemPromptResource);
        String userTemplate = promptTemplateService.load(nliUserPromptResource);

        String textA = truncate(chunkA.chunk().getChunkText(), CHUNK_TEXT_MAX_CHARS);
        String textB = truncate(chunkB.chunk().getChunkText(), CHUNK_TEXT_MAX_CHARS);

        String userPrompt = userTemplate
                .replace("{chunk_a}", textA)
                .replace("{chunk_b}", textB);

        List<ChatPromptMessage> messages = List.of(
                new ChatPromptMessage("system", systemPrompt),
                new ChatPromptMessage("user", userPrompt)
        );

        ChatCompletionResult completion = conversationChatClient.chat(
                chatModel.providerCode(), chatModel.modelCode(), messages);

        return parseNliResult(completion.content());
    }

    private List<RetrievalService.RetrievedChunk> resolveConflicts(
            List<RetrievalService.RetrievedChunk> chunks,
            List<ConflictGroup> conflicts) {

        if (!properties.getResolution().isEnabled() || conflicts.isEmpty()) {
            return markConflictGroups(chunks, conflicts);
        }

        Set<Long> suppressed = new HashSet<>();
        for (var group : conflicts) {
            if (group.getPreferredChunkId() != null) {
                for (Long chunkId : group.getChunkIds()) {
                    if (!chunkId.equals(group.getPreferredChunkId())) {
                        suppressed.add(chunkId);
                    }
                }
            }
        }

        if (suppressed.isEmpty()) {
            return markConflictGroups(chunks, conflicts);
        }

        List<RetrievalService.RetrievedChunk> resolved = chunks.stream()
                .filter(c -> !suppressed.contains(c.chunk().getId()))
                .toList();

        log.debug("冲突消解: 原始={}, 消解后={}, 抑制={}", chunks.size(), resolved.size(), suppressed.size());
        return resolved;
    }

    private List<RetrievalService.RetrievedChunk> markConflictGroups(
            List<RetrievalService.RetrievedChunk> chunks,
            List<ConflictGroup> conflicts) {

        Map<Long, List<String>> chunkToGroups = new HashMap<>();
        for (var group : conflicts) {
            for (Long chunkId : group.getChunkIds()) {
                chunkToGroups.computeIfAbsent(chunkId, k -> new ArrayList<>()).add(group.getGroupId());
            }
        }

        return chunks.stream()
                .map(c -> {
                    List<String> groups = chunkToGroups.getOrDefault(c.chunk().getId(), List.of());
                    if (groups.isEmpty()) {
                        return c;
                    }
                    return new RetrievalService.RetrievedChunk(c.chunk(), c.score(), groups);
                })
                .toList();
    }

    private Long resolveByVersion(List<RetrievalService.RetrievedChunk> docChunks) {
        RetrievalService.RetrievedChunk latest = null;
        int maxVersion = -1;

        for (var chunk : docChunks) {
            Long versionId = chunk.chunk().getDocumentVersionId();
            if (versionId != null && versionId.intValue() > maxVersion) {
                maxVersion = versionId.intValue();
                latest = chunk;
            }
        }

        return latest != null ? latest.chunk().getId() : null;
    }

    private Long resolveByMetadata(RetrievalService.RetrievedChunk a, RetrievalService.RetrievedChunk b) {
        // 版本优先
        Long verA = a.chunk().getDocumentVersionId();
        Long verB = b.chunk().getDocumentVersionId();
        if (verA != null && verB != null && !verA.equals(verB)) {
            return verA > verB ? a.chunk().getId() : b.chunk().getId();
        }

        // 时间优先
        if (a.chunk().getCreatedAt() != null && b.chunk().getCreatedAt() != null) {
            return a.chunk().getCreatedAt().isAfter(b.chunk().getCreatedAt())
                    ? a.chunk().getId() : b.chunk().getId();
        }

        return null;
    }

    private ExtractedInfo parseExtractedInfo(String response) {
        if (response == null || response.isBlank()) return null;
        try {
            String json = extractJson(response);
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            return new ExtractedInfo(
                    (String) map.getOrDefault("subject", ""),
                    (String) map.getOrDefault("attribute", ""),
                    (String) map.getOrDefault("conclusion", "")
            );
        } catch (Exception e) {
            log.debug("解析信息抽取结果失败: {}", e.getMessage());
            return null;
        }
    }

    private NliResult parseNliResult(String response) {
        if (response == null || response.isBlank()) {
            return new NliResult("neutral", 0.0, "");
        }
        try {
            String json = extractJson(response);
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            return new NliResult(
                    (String) map.getOrDefault("relation", "neutral"),
                    ((Number) map.getOrDefault("confidence", 0.0)).doubleValue(),
                    (String) map.getOrDefault("reason", "")
            );
        } catch (Exception e) {
            log.debug("解析 NLI 结果失败: {}", e.getMessage());
            return new NliResult("neutral", 0.0, "");
        }
    }

    private String extractJson(String response) {
        String trimmed = response.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String truncate(String text, int maxChars) {
        if (text == null) return "";
        return text.length() > maxChars ? text.substring(0, maxChars) + "..." : text;
    }

    record ExtractedInfo(String subject, String attribute, String conclusion) {}
    record NliResult(String relation, double confidence, String reason) {}
}
