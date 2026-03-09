package com.ragadmin.server.infra.ai.embedding;

import java.util.List;

public interface EmbeddingModelClient {

    boolean supports(String providerCode);

    List<List<Float>> embed(String modelCode, List<String> inputs);
}
