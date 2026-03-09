package com.ragadmin.server.infra.ai.embedding;

import com.ragadmin.server.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EmbeddingClientRegistry {

    private final List<EmbeddingModelClient> clients;

    public EmbeddingClientRegistry(List<EmbeddingModelClient> clients) {
        this.clients = clients;
    }

    public EmbeddingModelClient getClient(String providerCode) {
        return clients.stream()
                .filter(client -> client.supports(providerCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "EMBEDDING_PROVIDER_UNSUPPORTED",
                        "当前未实现该模型提供方的 Embedding 客户端",
                        HttpStatus.BAD_REQUEST
                ));
    }
}
