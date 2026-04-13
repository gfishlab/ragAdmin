package com.ragadmin.server.infra.ai.embedding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OllamaPropertiesTest {

    @Test
    void shouldUseLightweightLocalDefaults() {
        OllamaProperties properties = new OllamaProperties();

        assertEquals("qwen2.5:1.5b", properties.getDefaultChatModel());
        assertEquals("quentinz/bge-small-zh-v1.5", properties.getDefaultEmbeddingModel());
    }
}
