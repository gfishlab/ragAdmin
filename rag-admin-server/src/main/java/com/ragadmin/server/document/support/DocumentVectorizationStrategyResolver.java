package com.ragadmin.server.document.support;

import com.ragadmin.server.model.service.ModelService;
import org.springframework.stereotype.Component;

@Component
public class DocumentVectorizationStrategyResolver {

    private final ModelService modelService;
    private final DocumentVectorizationProperties vectorizationProperties;

    public DocumentVectorizationStrategyResolver(
            ModelService modelService,
            DocumentVectorizationProperties vectorizationProperties
    ) {
        this.modelService = modelService;
        this.vectorizationProperties = vectorizationProperties;
    }

    public ResolvedStrategy resolveByEmbeddingModelId(Long embeddingModelId) {
        EmbeddingModelDescriptor descriptor = modelService.resolveKnowledgeBaseEmbeddingModelDescriptor(embeddingModelId);
        return new ResolvedStrategy(descriptor, vectorizationProperties.resolve(descriptor.providerCode()));
    }

    public record ResolvedStrategy(
            EmbeddingModelDescriptor descriptor,
            DocumentVectorizationProperties.StrategyProperties strategy
    ) {
    }
}
