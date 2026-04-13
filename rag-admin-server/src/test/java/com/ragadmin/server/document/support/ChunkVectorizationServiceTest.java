package com.ragadmin.server.document.support;

import com.ragadmin.server.document.entity.ChunkEntity;
import com.ragadmin.server.document.entity.ChunkVectorRefEntity;
import com.ragadmin.server.document.mapper.ChunkVectorRefMapper;
import com.ragadmin.server.infra.ai.embedding.EmbeddingClientRegistry;
import com.ragadmin.server.infra.ai.embedding.EmbeddingExecutionMode;
import com.ragadmin.server.infra.ai.embedding.EmbeddingModelClient;
import com.ragadmin.server.infra.vector.MilvusVectorStoreClient;
import com.ragadmin.server.knowledge.entity.KnowledgeBaseEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChunkVectorizationServiceTest {

    @Mock
    private DocumentVectorizationStrategyResolver strategyResolver;

    @Mock
    private EmbeddingClientRegistry embeddingClientRegistry;

    @Mock
    private MilvusVectorStoreClient milvusVectorStoreClient;

    @Mock
    private ChunkVectorRefMapper chunkVectorRefMapper;

    @Mock
    private EmbeddingModelClient embeddingModelClient;

    @InjectMocks
    private ChunkVectorizationService chunkVectorizationService;

    @Test
    void shouldBatchEmbeddingRequestsByProviderStrategy() {
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(8L);
        knowledgeBase.setEmbeddingModelId(3L);

        when(strategyResolver.resolveByEmbeddingModelId(3L))
                .thenReturn(new DocumentVectorizationStrategyResolver.ResolvedStrategy(
                        new EmbeddingModelDescriptor(3L, "quentinz/bge-small-zh-v1.5", "OLLAMA", "Ollama", EmbeddingExecutionMode.SYNC_TEXT),
                        new DocumentVectorizationProperties.StrategyProperties(1, 400, 80)
                ));
        when(embeddingClientRegistry.getClient("OLLAMA")).thenReturn(embeddingModelClient);
        when(embeddingModelClient.embed(eq("quentinz/bge-small-zh-v1.5"), anyList()))
                .thenAnswer(invocation -> {
                    List<String> inputs = invocation.getArgument(1);
                    List<List<Float>> result = new ArrayList<>();
                    for (int index = 0; index < inputs.size(); index++) {
                        result.add(List.of(0.1F, 0.2F, 0.3F));
                    }
                    return result;
                });

        List<ChunkEntity> chunks = new ArrayList<>();
        for (int index = 1; index <= 26; index++) {
            ChunkEntity chunk = new ChunkEntity();
            chunk.setId((long) index);
            chunk.setKbId(8L);
            chunk.setDocumentId(18L);
            chunk.setDocumentVersionId(28L);
            chunk.setChunkNo(index);
            chunk.setChunkText("chunk-" + index);
            chunks.add(chunk);
        }

        chunkVectorizationService.vectorize(knowledgeBase, chunks);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> inputsCaptor = ArgumentCaptor.forClass(List.class);
        verify(embeddingModelClient, times(26)).embed(eq("quentinz/bge-small-zh-v1.5"), inputsCaptor.capture());
        assertEquals(1, inputsCaptor.getAllValues().getFirst().size());
        assertEquals(1, inputsCaptor.getAllValues().getLast().size());

        verify(milvusVectorStoreClient).ensureCollection("kb_8_emb_3_d_3", 3);
        verify(milvusVectorStoreClient).upsert(eq("kb_8_emb_3_d_3"), eq(chunks), anyList());
        verify(chunkVectorRefMapper, times(26)).insert(org.mockito.ArgumentMatchers.any(ChunkVectorRefEntity.class));
    }
}
