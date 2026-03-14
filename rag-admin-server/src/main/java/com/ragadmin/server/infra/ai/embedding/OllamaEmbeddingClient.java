package com.ragadmin.server.infra.ai.embedding;

import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.infra.ai.SpringAiModelSupport;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "rag.ai.ollama", name = "enabled", havingValue = "true")
public class OllamaEmbeddingClient implements EmbeddingModelClient {

    private final OllamaProperties ollamaProperties;

    public OllamaEmbeddingClient(OllamaProperties ollamaProperties) {
        this.ollamaProperties = ollamaProperties;
    }

    @Override
    public boolean supports(String providerCode) {
        return "OLLAMA".equalsIgnoreCase(providerCode);
    }

    @Override
    public List<List<Float>> embed(String modelCode, List<String> inputs) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(SpringAiModelSupport.normalizeOllamaOpenAiBaseUrl(ollamaProperties.getBaseUrl()))
                .apiKey("ollama")
                .restClientBuilder(SpringAiModelSupport.createRestClientBuilder(ollamaProperties.getTimeoutSeconds()))
                .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(modelCode)
                .build();
        OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(openAiApi, MetadataMode.NONE, options);
        EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(inputs, options));
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            throw new BusinessException("EMBEDDING_FAILED", "Ollama Embedding 返回为空", HttpStatus.BAD_GATEWAY);
        }
        return response.getResults().stream()
                .sorted(Comparator.comparingInt(Embedding::getIndex))
                .map(embedding -> SpringAiModelSupport.toFloatList(embedding.getOutput()))
                .toList();
    }
}
