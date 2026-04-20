package com.ragadmin.server.document.parser;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocumentChunkStrategyResolver {

    private final List<DocumentChunkStrategy> strategies;

    public DocumentChunkStrategyResolver(List<DocumentChunkStrategy> strategies) {
        this.strategies = strategies;
    }

    public DocumentChunkStrategy resolve(ChunkContext context) {
        return strategies.stream()
                .filter(s -> s.supports(context))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "无匹配的分块策略，docType=" + context.document().getDocType()));
    }
}
