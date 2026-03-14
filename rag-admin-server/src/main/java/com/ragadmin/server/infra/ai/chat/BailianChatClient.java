package com.ragadmin.server.infra.ai.chat;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.ragadmin.server.infra.ai.SpringAiModelSupport;
import com.ragadmin.server.infra.ai.bailian.BailianProperties;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "rag.ai.bailian", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BailianChatClient implements ChatModelClient {

    private final BailianProperties bailianProperties;

    public BailianChatClient(BailianProperties bailianProperties) {
        this.bailianProperties = bailianProperties;
    }

    @Override
    public boolean supports(String providerCode) {
        return "BAILIAN".equalsIgnoreCase(providerCode);
    }

    @Override
    public ChatCompletionResult chat(String modelCode, List<ChatMessage> messages) {
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .baseUrl(SpringAiModelSupport.normalizeDashScopeBaseUrl(bailianProperties.getBaseUrl()))
                .apiKey(SpringAiModelSupport.requireApiKey(bailianProperties.getApiKey()))
                .restClientBuilder(SpringAiModelSupport.createRestClientBuilder(bailianProperties.getTimeoutSeconds()))
                .build();
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .model(modelCode)
                .build();
        DashScopeChatModel chatModel = DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(options)
                .build();
        Prompt prompt = new Prompt(SpringAiModelSupport.toSpringMessages(messages), options);
        return SpringAiModelSupport.toChatCompletionResult(chatModel.call(prompt));
    }
}
