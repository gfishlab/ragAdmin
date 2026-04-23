package com.ragadmin.server.infra.ai.rerank;

import java.util.List;

public record RerankResult(List<RerankItem> results) {
    public record RerankItem(int index, double relevanceScore) {}
}
