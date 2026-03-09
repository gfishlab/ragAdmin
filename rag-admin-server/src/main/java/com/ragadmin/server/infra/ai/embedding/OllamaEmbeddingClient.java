package com.ragadmin.server.infra.ai.embedding;

import com.ragadmin.server.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

@Component
public class OllamaEmbeddingClient implements EmbeddingModelClient {

    private final RestClient restClient;

    public OllamaEmbeddingClient(OllamaProperties ollamaProperties) {
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
    public List<List<Float>> embed(String modelCode, List<String> inputs) {
        OllamaEmbedResponse response = restClient.post()
                .uri("/api/embed")
                .body(new OllamaEmbedRequest(modelCode, inputs))
                .retrieve()
                .body(OllamaEmbedResponse.class);
        if (response == null || response.embeddings() == null || response.embeddings().isEmpty()) {
            throw new BusinessException("EMBEDDING_FAILED", "Ollama Embedding 返回为空", HttpStatus.BAD_GATEWAY);
        }
        return response.embeddings();
    }

    private record OllamaEmbedRequest(String model, List<String> input) {
    }

    private record OllamaEmbedResponse(List<List<Float>> embeddings) {
    }
}
