package com.ragadmin.server.infra.ai.embedding;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.infra.ai.SpringAiModelSupport;
import com.ragadmin.server.infra.ai.bailian.BailianProperties;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "rag.ai.bailian", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BailianEmbeddingClient implements EmbeddingModelClient {

    private final BailianProperties bailianProperties;

    public BailianEmbeddingClient(BailianProperties bailianProperties) {
        this.bailianProperties = bailianProperties;
    }

    @Override
    public boolean supports(String providerCode) {
        return "BAILIAN".equalsIgnoreCase(providerCode);
    }

    @Override
    public List<List<Float>> embed(String modelCode, List<String> inputs) {
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .baseUrl(SpringAiModelSupport.normalizeDashScopeBaseUrl(bailianProperties.getBaseUrl()))
                .apiKey(SpringAiModelSupport.requireApiKey(bailianProperties.getApiKey()))
                .restClientBuilder(SpringAiModelSupport.createRestClientBuilder(bailianProperties.getTimeoutSeconds()))
                .build();
        DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
                .model(modelCode)
                .build();
        DashScopeEmbeddingModel embeddingModel = DashScopeEmbeddingModel.builder()
                .dashScopeApi(dashScopeApi)
                .metadataMode(MetadataMode.NONE)
                .defaultOptions(options)
                .build();
        EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(inputs, options));
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            throw new BusinessException("EMBEDDING_FAILED", "百炼 Embedding 返回为空", HttpStatus.BAD_GATEWAY);
        }
        return response.getResults().stream()
                .sorted(Comparator.comparingInt(Embedding::getIndex))
                .map(embedding -> SpringAiModelSupport.toFloatList(embedding.getOutput()))
                .toList();
    }
}
