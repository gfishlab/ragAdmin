package com.ragadmin.server.infra.ai;

import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.infra.ai.chat.ChatCompletionResult;
import com.ragadmin.server.infra.ai.chat.ChatPromptMessage;
import com.ragadmin.server.infra.ai.embedding.EmbeddingExecutionMode;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SpringAiModelSupport {

    private static final String DEFAULT_DASHSCOPE_BASE_URL = "https://dashscope.aliyuncs.com";

    /**
     * 当前项目默认向量维度按 1024 设计，因此百炼文本向量模型先收口到 v3 / v4，
     * 避免误配到视觉向量模型或 1536 维历史模型后让文档链路与存储设计发生漂移。
     */
    private static final Set<String> SUPPORTED_DASHSCOPE_SYNC_TEXT_EMBEDDING_MODELS = Set.of(
            "text-embedding-v3",
            "text-embedding-v4"
    );

    /**
     * 异步批量向量模型当前只做登记能力预留，后续需要单独补上传文件、任务轮询和结果回写链路。
     */
    private static final Set<String> SUPPORTED_DASHSCOPE_ASYNC_TEXT_EMBEDDING_MODELS = Set.of(
            "text-embedding-async-v1",
            "text-embedding-async-v2"
    );

    private SpringAiModelSupport() {
    }

    public static RestClient.Builder createRestClientBuilder(int timeoutSeconds) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return RestClient.builder().requestFactory(requestFactory);
    }

    public static String requireApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("BAILIAN_API_KEY_MISSING", "百炼 API Key 未配置", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return apiKey.trim();
    }

    public static String normalizeDashScopeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return DEFAULT_DASHSCOPE_BASE_URL;
        }
        String resolved = stripTrailingSlash(baseUrl.trim());

        // DashScope Java SDK 这里需要的是网关根地址，不能直接把 compatible-mode 或具体服务路径拼进来，
        // 否则后续再叠加 `/api/v1/services/**` 时会命中错误的接口。
        for (String marker : List.of("/compatible-mode/", "/api/v1/services/", "/api/v1", "/services/")) {
            int markerIndex = resolved.indexOf(marker);
            if (markerIndex > 0) {
                resolved = resolved.substring(0, markerIndex);
                break;
            }
        }
        return stripTrailingSlash(resolved);
    }

    public static String normalizeSupportedDashScopeEmbeddingModel(String modelCode) {
        if (!StringUtils.hasText(modelCode)) {
            throw new BusinessException("EMBEDDING_MODEL_INVALID", "Embedding 模型编码不能为空", HttpStatus.BAD_REQUEST);
        }
        String normalizedModelCode = modelCode.trim().toLowerCase();
        if (SUPPORTED_DASHSCOPE_SYNC_TEXT_EMBEDDING_MODELS.contains(normalizedModelCode)
                || SUPPORTED_DASHSCOPE_ASYNC_TEXT_EMBEDDING_MODELS.contains(normalizedModelCode)) {
            return normalizedModelCode;
        }
        if (looksLikeDashScopeMultimodalEmbeddingModel(normalizedModelCode)) {
            throw new BusinessException(
                    "EMBEDDING_MODEL_UNSUPPORTED",
                    "当前平台仅支持百炼文本 Embedding 模型；当前模型 "
                            + modelCode.trim()
                            + " 属于多模态向量模型，需要 input.url 或 input.contents 输入。",
                    HttpStatus.BAD_REQUEST
            );
        }
        throw new BusinessException(
                "EMBEDDING_MODEL_UNSUPPORTED",
                "当前平台仅支持百炼文本 Embedding 模型 text-embedding-v3、text-embedding-v4、text-embedding-async-v1、text-embedding-async-v2；当前模型 "
                        + modelCode.trim()
                        + " 不受支持。",
                HttpStatus.BAD_REQUEST
        );
    }

    public static EmbeddingExecutionMode resolveDashScopeEmbeddingExecutionMode(String modelCode) {
        String normalizedModelCode = normalizeSupportedDashScopeEmbeddingModel(modelCode);
        if (SUPPORTED_DASHSCOPE_ASYNC_TEXT_EMBEDDING_MODELS.contains(normalizedModelCode)) {
            return EmbeddingExecutionMode.ASYNC_BATCH;
        }
        return EmbeddingExecutionMode.SYNC_TEXT;
    }

    /**
     * 当前文档解析与检索链路走的是纯文本切片输入，因此需要显式拦截百炼多模态向量模型，
     * 同时也要拦截尚未接入的异步批量文本模型，避免把底层 `input.url` 要求直接暴露给上层业务。
     */
    public static String requireSupportedDashScopeTextEmbeddingModel(String modelCode) {
        String normalizedModelCode = normalizeSupportedDashScopeEmbeddingModel(modelCode);
        if (SUPPORTED_DASHSCOPE_ASYNC_TEXT_EMBEDDING_MODELS.contains(normalizedModelCode)) {
            throw new BusinessException(
                    "EMBEDDING_MODEL_EXECUTION_MODE_UNSUPPORTED",
                    "当前知识库文档解析与检索链路仅支持同步文本 Embedding 模型 text-embedding-v3 或 text-embedding-v4；异步批量模型 "
                            + modelCode.trim()
                            + " 已预留接入扩展点，但暂未实现运行链路。",
                    HttpStatus.BAD_REQUEST
            );
        }
        return normalizedModelCode;
    }

    public static String normalizeOllamaOpenAiBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException("OLLAMA_BASE_URL_MISSING", "Ollama baseUrl 未配置", HttpStatus.SERVICE_UNAVAILABLE);
        }
        String resolved = baseUrl.trim();
        resolved = resolved.endsWith("/") ? resolved.substring(0, resolved.length() - 1) : resolved;
        if (resolved.endsWith("/v1")) {
            return resolved;
        }
        return resolved + "/v1";
    }

    public static List<Message> toSpringMessages(List<ChatPromptMessage> messages) {
        List<Message> springMessages = new ArrayList<>(messages.size());
        for (ChatPromptMessage message : messages) {
            String role = message.role() == null ? "user" : message.role().trim().toLowerCase();
            switch (role) {
                case "system" -> springMessages.add(new SystemMessage(message.content()));
                case "assistant" -> springMessages.add(new AssistantMessage(message.content()));
                default -> springMessages.add(new UserMessage(message.content()));
            }
        }
        return springMessages;
    }

    public static ChatCompletionResult toChatCompletionResult(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new BusinessException("CHAT_FAILED", "模型聊天返回为空", HttpStatus.BAD_GATEWAY);
        }
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        return new ChatCompletionResult(
                response.getResult().getOutput().getText(),
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens()
        );
    }

    public static List<Float> toFloatList(float[] values) {
        List<Float> result = new ArrayList<>(values.length);
        for (float value : values) {
            result.add(value);
        }
        return result;
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static boolean looksLikeDashScopeMultimodalEmbeddingModel(String normalizedModelCode) {
        return normalizedModelCode.contains("vl-embedding")
                || normalizedModelCode.contains("multimodal-embedding")
                || normalizedModelCode.contains("embedding-vision");
    }
}
