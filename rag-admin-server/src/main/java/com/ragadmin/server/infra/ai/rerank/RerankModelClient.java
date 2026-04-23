package com.ragadmin.server.infra.ai.rerank;

import java.util.List;

public interface RerankModelClient {
    boolean supports(String providerCode);
    RerankResult rerank(String modelCode, String query, List<String> documents);
    void healthCheck(String modelCode);
}
