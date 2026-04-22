package com.ragadmin.server.retrieval.config;

public enum RetrievalMode {

    SEMANTIC_ONLY,
    KEYWORD_ONLY,
    HYBRID;

    public static RetrievalMode resolve(String value) {
        if (value == null || value.isBlank()) {
            return SEMANTIC_ONLY;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SEMANTIC_ONLY;
        }
    }
}
