package com.ragadmin.server.infra.ai;

import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.infra.ai.chat.ChatModelClient;
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

public final class SpringAiModelSupport {

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
            return "https://dashscope.aliyuncs.com";
        }
        return baseUrl.trim();
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

    public static List<Message> toSpringMessages(List<ChatModelClient.ChatMessage> messages) {
        List<Message> springMessages = new ArrayList<>(messages.size());
        for (ChatModelClient.ChatMessage message : messages) {
            String role = message.role() == null ? "user" : message.role().trim().toLowerCase();
            switch (role) {
                case "system" -> springMessages.add(new SystemMessage(message.content()));
                case "assistant" -> springMessages.add(new AssistantMessage(message.content()));
                default -> springMessages.add(new UserMessage(message.content()));
            }
        }
        return springMessages;
    }

    public static ChatModelClient.ChatCompletionResult toChatCompletionResult(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new BusinessException("CHAT_FAILED", "模型聊天返回为空", HttpStatus.BAD_GATEWAY);
        }
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        return new ChatModelClient.ChatCompletionResult(
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
}
