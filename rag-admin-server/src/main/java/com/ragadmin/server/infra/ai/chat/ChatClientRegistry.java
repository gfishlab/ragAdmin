package com.ragadmin.server.infra.ai.chat;

import com.ragadmin.server.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ChatClientRegistry {

    private final List<ChatModelClient> clients;

    public ChatClientRegistry(List<ChatModelClient> clients) {
        this.clients = clients;
    }

    public ChatModelClient getClient(String providerCode) {
        return clients.stream()
                .filter(client -> client.supports(providerCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "CHAT_PROVIDER_UNSUPPORTED",
                        "当前未实现该模型提供方的聊天客户端",
                        HttpStatus.BAD_REQUEST
                ));
    }
}
