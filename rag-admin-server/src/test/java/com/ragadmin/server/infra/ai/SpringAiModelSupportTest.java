package com.ragadmin.server.infra.ai;

import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.infra.ai.embedding.EmbeddingExecutionMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringAiModelSupportTest {

    @Test
    void shouldNormalizeDashScopeBaseUrlToGatewayRoot() {
        assertEquals("https://dashscope.aliyuncs.com",
                SpringAiModelSupport.normalizeDashScopeBaseUrl("https://dashscope.aliyuncs.com"));
        assertEquals("https://dashscope.aliyuncs.com",
                SpringAiModelSupport.normalizeDashScopeBaseUrl("https://dashscope.aliyuncs.com/api/v1"));
        assertEquals("https://dashscope.aliyuncs.com",
                SpringAiModelSupport.normalizeDashScopeBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1"));
        assertEquals("https://dashscope.aliyuncs.com",
                SpringAiModelSupport.normalizeDashScopeBaseUrl(
                        "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding"
                ));
    }

    @Test
    void shouldRejectVisionEmbeddingModelForTextPipeline() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> SpringAiModelSupport.requireSupportedDashScopeTextEmbeddingModel("tongyi-embedding-vision-plus")
        );

        assertEquals("EMBEDDING_MODEL_UNSUPPORTED", exception.getCode());
        assertTrue(exception.getMessage().contains("input.url"));
    }

    @Test
    void shouldResolveAsyncBatchExecutionModeForDashScopeAsyncEmbeddingModel() {
        assertEquals("text-embedding-async-v2",
                SpringAiModelSupport.normalizeSupportedDashScopeEmbeddingModel(" text-embedding-async-v2 "));
        assertEquals(EmbeddingExecutionMode.ASYNC_BATCH,
                SpringAiModelSupport.resolveDashScopeEmbeddingExecutionMode("text-embedding-async-v2"));
    }

    @Test
    void shouldRejectAsyncEmbeddingModelForCurrentSyncPipeline() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> SpringAiModelSupport.requireSupportedDashScopeTextEmbeddingModel("text-embedding-async-v2")
        );

        assertEquals("EMBEDDING_MODEL_EXECUTION_MODE_UNSUPPORTED", exception.getCode());
        assertTrue(exception.getMessage().contains("已预留接入扩展点"));
    }
}
