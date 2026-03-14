package com.ragadmin.server.infra.ai.chat;

import com.ragadmin.server.infra.ai.SpringAiModelSupport;
import com.ragadmin.server.infra.ai.embedding.OllamaProperties;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "rag.ai.ollama", name = "enabled", havingValue = "true")
public class OllamaChatClient implements ChatModelClient {

    private final OllamaProperties ollamaProperties;

    public OllamaChatClient(OllamaProperties ollamaProperties) {
        this.ollamaProperties = ollamaProperties;
    }

    @Override
    public boolean supports(String providerCode) {
        return "OLLAMA".equalsIgnoreCase(providerCode);
    }

    @Override
    public ChatCompletionResult chat(String modelCode, List<ChatMessage> messages) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(SpringAiModelSupport.normalizeOllamaOpenAiBaseUrl(ollamaProperties.getBaseUrl()))
                .apiKey("ollama")
                .restClientBuilder(SpringAiModelSupport.createRestClientBuilder(ollamaProperties.getTimeoutSeconds()))
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(modelCode)
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
        Prompt prompt = new Prompt(SpringAiModelSupport.toSpringMessages(messages), options);
        return SpringAiModelSupport.toChatCompletionResult(chatModel.call(prompt));
    }
}
