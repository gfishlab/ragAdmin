package com.ragadmin.server.infra.ai.chat;

import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.infra.ai.embedding.OllamaProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

@Component
public class OllamaChatClient implements ChatModelClient {

    private final RestClient restClient;

    public OllamaChatClient(OllamaProperties ollamaProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(ollamaProperties.getTimeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(ollamaProperties.getTimeoutSeconds()));
        this.restClient = RestClient.builder()
                .baseUrl(ollamaProperties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public boolean supports(String providerCode) {
        return "OLLAMA".equalsIgnoreCase(providerCode);
    }

    @Override
    public ChatCompletionResult chat(String modelCode, List<ChatMessage> messages) {
        OllamaChatResponse response = restClient.post()
                .uri("/api/chat")
                .body(new OllamaChatRequest(modelCode, messages, false))
                .retrieve()
                .body(OllamaChatResponse.class);
        if (response == null || response.message() == null || response.message().content() == null) {
            throw new BusinessException("CHAT_FAILED", "Ollama 聊天返回为空", HttpStatus.BAD_GATEWAY);
        }
        return new ChatCompletionResult(
                response.message().content(),
                null,
                response.evalCount()
        );
    }

    private record OllamaChatRequest(String model, List<ChatMessage> messages, boolean stream) {
    }

    private record OllamaChatResponse(OllamaResponseMessage message, Integer promptEvalCount, Integer evalCount) {
    }

    private record OllamaResponseMessage(String role, String content) {
    }
}
