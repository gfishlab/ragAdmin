package com.ragadmin.server.infra.ai.rerank;

import com.ragadmin.server.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RerankClientRegistry {

    private final List<RerankModelClient> clients;

    public RerankClientRegistry(List<RerankModelClient> clients) {
        this.clients = clients;
    }

    public RerankModelClient getClient(String providerCode) {
        return clients.stream()
                .filter(client -> client.supports(providerCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "RERANK_PROVIDER_UNSUPPORTED",
                        "当前未实现该模型提供方的 Rerank 客户端",
                        HttpStatus.BAD_REQUEST
                ));
    }
}
