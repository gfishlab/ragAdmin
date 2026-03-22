package com.ragadmin.server.infra.ai.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 基于结构化输出的回答后处理元数据生成器。
 * 该链路不影响主回答成功返回，模型失败时必须退回规则结果。
 */
@Component
public class DefaultChatAnswerMetadataGenerationService implements ChatAnswerMetadataGenerationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultChatAnswerMetadataGenerationService.class);

    private static final String METADATA_SYSTEM_PROMPT = """
            你是问答质量标注助手。
            你的任务不是重写回答，而是为已经生成完成的回答补充稳定、保守、可机读的元数据。
            请只返回结构化字段，不要重复回答正文。
            约束如下：
            1. confidence 只能是 HIGH、MEDIUM、LOW
            2. hasKnowledgeBaseEvidence 表示当前回答是否有知识库证据支撑
            3. needFollowUp 表示是否建议继续追问、人工复核或补充核验
            4. 如果无法明确判断，优先保守输出
            """;

    private static final List<String> FOLLOW_UP_HINTS = List.of(
            "无法确认",
            "不确定",
            "不清楚",
            "需要进一步",
            "需进一步",
            "建议核实",
            "建议进一步确认",
            "仅供参考"
    );

    private final ConversationChatClient conversationChatClient;

    private final ChatAnswerMetadataProperties properties;

    public DefaultChatAnswerMetadataGenerationService(
            ConversationChatClient conversationChatClient,
            ChatAnswerMetadataProperties properties
    ) {
        this.conversationChatClient = conversationChatClient;
        this.properties = properties;
    }

    @Override
    public ChatAnswerMetadata generate(ChatAnswerMetadataGenerationRequest request) {
        ChatAnswerMetadata fallback = buildRuleBasedMetadata(request);
        if (!canUseModel(request)) {
            logMetadata(request, fallback, "RULE_BASED");
            return fallback;
        }

        try {
            ChatAnswerMetadataStructuredOutput output = conversationChatClient.chatEntity(
                    request.providerCode(),
                    request.modelCode(),
                    buildPromptMessages(request),
                    ChatAnswerMetadataStructuredOutput.class
            );
            ChatAnswerMetadata metadata = mergeMetadata(request, fallback, output);
            logMetadata(request, metadata, "MODEL");
            return metadata;
        } catch (Exception ex) {
            log.warn(
                    "回答后处理元数据模型调用失败，已回退规则结果，providerCode={}, modelCode={}, referenceCount={}",
                    request.providerCode(),
                    request.modelCode(),
                    request.referenceCount(),
                    ex
            );
            logMetadata(request, fallback, "RULE_BASED");
            return fallback;
        }
    }

    private boolean canUseModel(ChatAnswerMetadataGenerationRequest request) {
        if (!properties.isEnabled()) {
            return false;
        }
        if (request == null || !StringUtils.hasText(request.answer())) {
            return false;
        }
        return StringUtils.hasText(request.providerCode()) && StringUtils.hasText(request.modelCode());
    }

    private List<ChatPromptMessage> buildPromptMessages(ChatAnswerMetadataGenerationRequest request) {
        String userPrompt = """
                用户问题：%s
                
                当前回答：%s
                
                当前上下文：
                - referenceCount=%d
                
                输出要求：
                1. confidence 只能是 HIGH、MEDIUM、LOW
                2. 如果 referenceCount=0，hasKnowledgeBaseEvidence 通常应为 false
                3. 仅当回答存在不确定性、明显不足或建议继续核验时，needFollowUp 才为 true
                4. 不要输出正文解释
                """.formatted(
                defaultText(request.question()),
                request.answer(),
                request.referenceCount()
        );
        return List.of(
                new ChatPromptMessage("system", METADATA_SYSTEM_PROMPT),
                new ChatPromptMessage("user", userPrompt)
        );
    }

    private ChatAnswerMetadata mergeMetadata(
            ChatAnswerMetadataGenerationRequest request,
            ChatAnswerMetadata fallback,
            ChatAnswerMetadataStructuredOutput output
    ) {
        boolean hasKnowledgeBaseEvidence = request.referenceCount() > 0
                && (output == null || output.hasKnowledgeBaseEvidence() == null
                ? fallback.hasKnowledgeBaseEvidence()
                : output.hasKnowledgeBaseEvidence());
        boolean needFollowUp = fallback.needFollowUp()
                || (output != null && Boolean.TRUE.equals(output.needFollowUp()));
        String confidence = normalizeConfidence(output == null ? null : output.confidence(), fallback.confidence());
        return new ChatAnswerMetadata(confidence, hasKnowledgeBaseEvidence, needFollowUp);
    }

    private ChatAnswerMetadata buildRuleBasedMetadata(ChatAnswerMetadataGenerationRequest request) {
        String normalizedAnswer = normalize(request == null ? null : request.answer());
        boolean hasKnowledgeBaseEvidence = request != null && request.referenceCount() > 0;
        boolean needFollowUp = !StringUtils.hasText(normalizedAnswer)
                || normalizedAnswer.length() < 10
                || containsFollowUpHint(normalizedAnswer);
        String confidence = hasKnowledgeBaseEvidence ? "MEDIUM" : "LOW";
        return new ChatAnswerMetadata(confidence, hasKnowledgeBaseEvidence, needFollowUp);
    }

    private boolean containsFollowUpHint(String answer) {
        if (!StringUtils.hasText(answer)) {
            return true;
        }
        return FOLLOW_UP_HINTS.stream().anyMatch(answer::contains);
    }

    private String normalizeConfidence(String rawConfidence, String fallbackConfidence) {
        if (!StringUtils.hasText(rawConfidence)) {
            return fallbackConfidence;
        }
        String normalized = rawConfidence.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "HIGH", "MEDIUM", "LOW" -> normalized;
            default -> fallbackConfidence;
        };
    }

    private void logMetadata(ChatAnswerMetadataGenerationRequest request, ChatAnswerMetadata metadata, String source) {
        if (!properties.isLogMetadata() || request == null || metadata == null) {
            return;
        }
        log.info(
                "回答元数据生成完成，source={}, confidence={}, hasKnowledgeBaseEvidence={}, needFollowUp={}, referenceCount={}, answerLength={}, providerCode={}, modelCode={}",
                source,
                metadata.confidence(),
                metadata.hasKnowledgeBaseEvidence(),
                metadata.needFollowUp(),
                request.referenceCount(),
                safeLength(request.answer()),
                request.providerCode(),
                request.modelCode()
        );
    }

    private String normalize(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.trim().replaceAll("\\s+", " ");
    }

    private int safeLength(String text) {
        return StringUtils.hasText(text) ? text.trim().length() : 0;
    }

    private String defaultText(String text) {
        return StringUtils.hasText(text) ? text.trim() : "";
    }
}
