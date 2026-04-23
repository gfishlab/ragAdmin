package com.ragadmin.server.infra.ai.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.ai.ollama")
public class OllamaProperties {

    private boolean enabled = false;
    private String baseUrl;
    private int timeoutSeconds = 30;
    private String defaultChatModel = "qwen2.5:1.5b";
    private String defaultEmbeddingModel = "quentinz/bge-small-zh-v1.5";
    private String defaultRerankerModel;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getDefaultChatModel() {
        return defaultChatModel;
    }

    public void setDefaultChatModel(String defaultChatModel) {
        this.defaultChatModel = defaultChatModel;
    }

    public String getDefaultEmbeddingModel() {
        return defaultEmbeddingModel;
    }

    public void setDefaultEmbeddingModel(String defaultEmbeddingModel) {
        this.defaultEmbeddingModel = defaultEmbeddingModel;
    }

    public String getDefaultRerankerModel() {
        return defaultRerankerModel;
    }

    public void setDefaultRerankerModel(String defaultRerankerModel) {
        this.defaultRerankerModel = defaultRerankerModel;
    }
}
