package com.ragadmin.server.document.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "rag.document.vectorize")
public class DocumentVectorizationProperties {

    /**
     * 全局默认策略，主要用于未命中 provider 专属配置时兜底。
     */
    private StrategyProperties defaults = new StrategyProperties();

    /**
     * 按模型提供方区分切片与向量化策略，避免不同 provider 的上下文窗口和批量能力互相污染。
     */
    private Map<String, StrategyProperties> providerStrategies = new HashMap<>();

    public StrategyProperties getDefaults() {
        return defaults;
    }

    public void setDefaults(StrategyProperties defaults) {
        this.defaults = defaults;
    }

    public Map<String, StrategyProperties> getProviderStrategies() {
        return providerStrategies;
    }

    public void setProviderStrategies(Map<String, StrategyProperties> providerStrategies) {
        this.providerStrategies = providerStrategies;
    }

    public StrategyProperties resolve(String providerCode) {
        if (providerCode == null) {
            return defaults;
        }
        return providerStrategies.getOrDefault(providerCode.toUpperCase(), defaults);
    }

    public static class StrategyProperties {

        /**
         * 每次向量化请求发送的切片数量；值越大吞吐越高，但更容易触发本地小模型上下文上限。
         */
        private int embeddingBatchSize = 10;

        /**
         * 单个切片的目标最大字符数。
         */
        private int maxChunkChars = 800;

        /**
         * 切片间重叠字符数，用于降低上下文断裂。
         */
        private int chunkOverlapChars = 120;

        public StrategyProperties() {
        }

        public StrategyProperties(int embeddingBatchSize, int maxChunkChars, int chunkOverlapChars) {
            this.embeddingBatchSize = embeddingBatchSize;
            this.maxChunkChars = maxChunkChars;
            this.chunkOverlapChars = chunkOverlapChars;
        }

        public int getEmbeddingBatchSize() {
            return embeddingBatchSize;
        }

        public void setEmbeddingBatchSize(int embeddingBatchSize) {
            this.embeddingBatchSize = embeddingBatchSize;
        }

        public int getMaxChunkChars() {
            return maxChunkChars;
        }

        public void setMaxChunkChars(int maxChunkChars) {
            this.maxChunkChars = maxChunkChars;
        }

        public int getChunkOverlapChars() {
            return chunkOverlapChars;
        }

        public void setChunkOverlapChars(int chunkOverlapChars) {
            this.chunkOverlapChars = chunkOverlapChars;
        }
    }
}
