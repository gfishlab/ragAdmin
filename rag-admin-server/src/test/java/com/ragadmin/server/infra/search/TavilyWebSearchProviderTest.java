package com.ragadmin.server.infra.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClientResponseException;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TavilyWebSearchProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpServer server;
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>("""
            {"request_id":"req-default","response_time":0.01,"results":[]}
            """);
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorizationHeader = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/search", exchange -> {
            authorizationHeader.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
            exchange.sendResponseHeaders(responseStatus.get(), bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldBeUnavailableWhenApiKeyMissing() {
        TavilyProperties tavilyProperties = new TavilyProperties();
        tavilyProperties.setBaseUrl(serverBaseUrl());
        tavilyProperties.setApiKey(null);

        TavilyWebSearchProvider provider = new TavilyWebSearchProvider(
                TavilyApiSupport.buildRestClient(tavilyProperties),
                tavilyProperties,
                new WebSearchProperties()
        );

        assertFalse(provider.isAvailable());
    }

    @Test
    void shouldMapAndTrimSearchResults() throws Exception {
        responseBody.set("""
                {
                  "request_id": "req-123",
                  "response_time": 1.23,
                  "results": [
                    {
                      "title": "这是一个很长很长的标题，用来验证标题裁剪逻辑是否生效",
                      "url": "https://example.com/news-1",
                      "content": "这是一段非常长的联网搜索摘要，用来验证摘要裁剪逻辑是否会在进入问答编排前先被截断，避免 prompt 被联网结果撑爆。",
                      "published_date": "2026-03-20T10:00:00Z"
                    },
                    {
                      "title": "第二条结果",
                      "url": "https://example.com/news-2",
                      "content": "第二条摘要"
                    }
                  ]
                }
                """);

        WebSearchProperties webSearchProperties = new WebSearchProperties();
        webSearchProperties.setResultTitleMaxChars(12);
        webSearchProperties.setResultSnippetMaxChars(16);

        TavilyProperties tavilyProperties = configuredProperties();
        tavilyProperties.setMaxResults(1);
        TavilyWebSearchProvider provider = new TavilyWebSearchProvider(
                TavilyApiSupport.buildRestClient(tavilyProperties),
                tavilyProperties,
                webSearchProperties
        );

        var snippets = provider.search("智能体行业动态", 3);

        assertEquals(1, snippets.size());
        assertEquals("Bearer tvly-test-key", authorizationHeader.get());
        JsonNode requestJson = objectMapper.readTree(requestBody.get());
        assertEquals("智能体行业动态", requestJson.get("query").asText());
        assertEquals("basic", requestJson.get("search_depth").asText());
        assertEquals("general", requestJson.get("topic").asText());
        assertEquals(1, requestJson.get("max_results").asInt());
        assertTrue(snippets.getFirst().title().endsWith("..."));
        assertTrue(snippets.getFirst().snippet().endsWith("..."));
        assertEquals("https://example.com/news-1", snippets.getFirst().url());
        assertEquals(Instant.parse("2026-03-20T10:00:00Z"), snippets.getFirst().publishedAt());
    }

    @Test
    void shouldThrowWhenTavilyReturnsHttpError() {
        responseStatus.set(500);
        responseBody.set("""
                {"error":"server error"}
                """);

        TavilyProperties tavilyProperties = configuredProperties();
        TavilyWebSearchProvider provider = new TavilyWebSearchProvider(
                TavilyApiSupport.buildRestClient(tavilyProperties),
                tavilyProperties,
                new WebSearchProperties()
        );

        RestClientResponseException exception = assertThrows(
                RestClientResponseException.class,
                () -> provider.search("天气", 1)
        );

        assertNotNull(exception.getStatusCode());
        assertEquals(500, exception.getStatusCode().value());
    }

    private TavilyProperties configuredProperties() {
        TavilyProperties properties = new TavilyProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(serverBaseUrl());
        properties.setApiKey("tvly-test-key");
        properties.setTimeoutSeconds(5);
        properties.setSearchDepth("basic");
        properties.setTopic("general");
        properties.setMaxResults(5);
        return properties;
    }

    private String serverBaseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
