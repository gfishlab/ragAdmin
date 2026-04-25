package com.ragadmin.server.document.support;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "rag.document.vectorize")
public class DocumentVectorizationProperties {

    /** 全局默认策略，主要用于未命中 provider 专属配置时兜底。 */
    private StrategyProperties defaults = new StrategyProperties();

    /** 按模型提供方区分切片与向量化策略，避免不同 provider 的上下文窗口和批量能力互相污染。 */
    private Map<String, StrategyProperties> providerStrategies = new HashMap<>();

    public StrategyProperties resolve(String providerCode) {
        if (providerCode == null) {
            return defaults;
        }
        return providerStrategies.getOrDefault(providerCode.toUpperCase(), defaults);
    }

    @Data
    public static class StrategyProperties {

        /** 每次向量化请求发送的切片数量。 */
        private int embeddingBatchSize = 10;

        /** 单个切片的目标最大字符数。@deprecated 使用 {@link com.ragadmin.server.document.parser.ChunkProperties} 替代 */
        @Deprecated
        private int maxChunkChars = 800;

        /** 切片间重叠字符数，用于降低上下文断裂。@deprecated 使用 ChunkProperties 替代 */
        @Deprecated
        private int chunkOverlapChars = 120;

        /** 切片最小字符数，低于此值的切片会被合并。@deprecated 使用 ChunkProperties 替代 */
        @Deprecated
        private int minChunkChars = 50;

        public StrategyProperties() {
        }

        public StrategyProperties(int embeddingBatchSize, int maxChunkChars, int chunkOverlapChars) {
            this(embeddingBatchSize, maxChunkChars, chunkOverlapChars, 50);
        }

        public StrategyProperties(int embeddingBatchSize, int maxChunkChars, int chunkOverlapChars, int minChunkChars) {
            this.embeddingBatchSize = embeddingBatchSize;
            this.maxChunkChars = maxChunkChars;
            this.chunkOverlapChars = chunkOverlapChars;
            this.minChunkChars = minChunkChars;
        }
    }
}
