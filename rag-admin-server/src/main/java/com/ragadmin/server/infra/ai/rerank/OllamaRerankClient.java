package com.ragadmin.server.infra.ai.rerank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.infra.ai.SpringAiModelSupport;
import com.ragadmin.server.infra.ai.embedding.OllamaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "rag.ai.ollama", name = "enabled", havingValue = "true")
public class OllamaRerankClient implements RerankModelClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaRerankClient.class);

    private static final String SYSTEM_PROMPT = """
            你是一个文档相关性评分专家。对每个文档按与查询的相关性打分，分数范围 0.0 到 1.0。
            直接输出 JSON 数组，不要有其他文字。格式：[{"index": 0, "score": 0.95}, {"index": 1, "score": 0.3}]""";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OllamaRerankClient(OllamaProperties ollamaProperties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = SpringAiModelSupport.createRestClientBuilder(ollamaProperties.getTimeoutSeconds())
                .baseUrl(ollamaProperties.getBaseUrl())
                .build();
    }

    @Override
    public boolean supports(String providerCode) {
        return "OLLAMA".equalsIgnoreCase(providerCode);
    }

    @Override
    public RerankResult rerank(String modelCode, String query, List<String> documents) {
        StringBuilder userContent = new StringBuilder();
        userContent.append("查询：").append(query).append("\n\n文档列表：\n");
        for (int i = 0; i < documents.size(); i++) {
            userContent.append(i).append(". ").append(documents.get(i)).append("\n");
        }

        String response = callChatApi(modelCode, SYSTEM_PROMPT, userContent.toString());
        return parseRerankResult(response);
    }

    @Override
    public void healthCheck(String modelCode) {
        try {
            callChatApi(modelCode, "Reply with OK.", "ping");
            log.debug("Reranker 模型 {} 健康检查通过", modelCode);
        } catch (Exception e) {
            throw new BusinessException("RERANK_HEALTH_CHECK_FAILED",
                    "Reranker 模型健康检查失败: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    private String callChatApi(String modelCode, String systemPrompt, String userContent) {
        try {
            String body = objectMapper.writeValueAsString(new ChatRequest(
                    modelCode,
                    List.of(
                            new Message("system", systemPrompt),
                            new Message("user", userContent)
                    ),
                    false
            ));

            String response = restClient.post()
                    .uri("/api/chat")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return extractMessageContent(response);
        } catch (Exception e) {
            throw new BusinessException("RERANK_CALL_FAILED",
                    "Reranker 模型调用失败: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    private String extractMessageContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode contentNode = root.get("message") != null ? root.get("message").get("content") : null;
            if (contentNode != null) {
                return contentNode.asText();
            }
            return responseBody;
        } catch (Exception e) {
            return responseBody;
        }
    }

    private RerankResult parseRerankResult(String content) {
        try {
            String json = content.trim();
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }

            List<RerankResult.RerankItem> items = new ArrayList<>();
            JsonNode array = objectMapper.readTree(json);
            for (JsonNode item : array) {
                JsonNode indexNode = item.get("index");
                JsonNode scoreNode = item.get("score");
                if (indexNode != null && scoreNode != null) {
                    double score = scoreNode.asDouble();
                    items.add(new RerankResult.RerankItem(
                            indexNode.asInt(),
                            Math.max(0.0, Math.min(1.0, score))
                    ));
                }
            }
            return new RerankResult(items);
        } catch (Exception e) {
            log.warn("解析 Reranker 响应失败: {}, 原始内容: {}", e.getMessage(), content);
            throw new BusinessException("RERANK_PARSE_FAILED",
                    "Reranker 响应解析失败: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    record ChatRequest(String model, List<Message> messages, boolean stream) {}
    record Message(String role, String content) {}
}
