package com.ragadmin.server.infra.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Tavily 联网搜索实现。
 */
public class TavilyWebSearchProvider implements WebSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(TavilyWebSearchProvider.class);

    private final RestClient restClient;
    private final TavilyProperties tavilyProperties;
    private final WebSearchProperties webSearchProperties;

    public TavilyWebSearchProvider(
            RestClient restClient,
            TavilyProperties tavilyProperties,
            WebSearchProperties webSearchProperties
    ) {
        this.restClient = restClient;
        this.tavilyProperties = tavilyProperties;
        this.webSearchProperties = webSearchProperties;
    }

    @Override
    public List<WebSearchSnippet> search(String query, int topK) {
        if (!isAvailable()) {
            log.info("Tavily 联网搜索已跳过，原因=provider_unavailable");
            return List.of();
        }
        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        int resolvedTopK = resolveTopK(topK);
        long startNanos = System.nanoTime();
        try {
            TavilySearchResponse response = restClient.post()
                    .uri("/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TavilySearchRequest(
                            query.trim(),
                            tavilyProperties.getTopic(),
                            tavilyProperties.getSearchDepth(),
                            resolvedTopK,
                            Boolean.TRUE
                    ))
                    .retrieve()
                    .body(TavilySearchResponse.class);
            List<WebSearchSnippet> snippets = response == null
                    ? List.of()
                    : normalizeResults(response.results(), resolvedTopK);
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.info(
                    "Tavily 联网搜索完成，requestId={}, query={}, queryLength={}, requestedTopK={}, resultCount={}, latencyMs={}, responseTime={}",
                    response == null ? "" : safeText(response.requestId()),
                    abbreviateForLog(query),
                    query.length(),
                    resolvedTopK,
                    snippets.size(),
                    latencyMs,
                    response == null ? null : response.responseTime()
            );
            return snippets;
        } catch (RestClientResponseException ex) {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.warn(
                    "Tavily 联网搜索失败，reason=http_error, statusCode={}, query={}, queryLength={}, requestedTopK={}, latencyMs={}, responseBody={}",
                    ex.getStatusCode().value(),
                    abbreviateForLog(query),
                    query.length(),
                    resolvedTopK,
                    latencyMs,
                    abbreviate(ex.getResponseBodyAsString(), Math.max(60, webSearchProperties.getLogQueryMaxChars()))
            );
            throw ex;
        } catch (Exception ex) {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.warn(
                    "Tavily 联网搜索失败，reason={}, query={}, queryLength={}, requestedTopK={}, latencyMs={}",
                    ex.getClass().getSimpleName(),
                    abbreviateForLog(query),
                    query.length(),
                    resolvedTopK,
                    latencyMs,
                    ex
            );
            throw ex;
        }
    }

    @Override
    public boolean isAvailable() {
        return webSearchProperties != null
                && webSearchProperties.isEnabled()
                && tavilyProperties != null
                && tavilyProperties.isConfigured();
    }

    private List<WebSearchSnippet> normalizeResults(List<TavilySearchResult> results, int resolvedTopK) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        return results.stream()
                .filter(Objects::nonNull)
                .limit(resolvedTopK)
                .map(this::toSnippet)
                .filter(Objects::nonNull)
                .toList();
    }

    private WebSearchSnippet toSnippet(TavilySearchResult result) {
        String title = truncate(normalizeText(result.title()), webSearchProperties.getResultTitleMaxChars());
        String snippet = truncate(normalizeText(result.content()), webSearchProperties.getResultSnippetMaxChars());
        String url = safeText(result.url());
        if (!StringUtils.hasText(title) && !StringUtils.hasText(snippet) && !StringUtils.hasText(url)) {
            return null;
        }
        return new WebSearchSnippet(
                title,
                snippet,
                url,
                parseInstant(result.publishedDate())
        );
    }

    private int resolveTopK(int requestedTopK) {
        int safeRequestedTopK = requestedTopK > 0 ? requestedTopK : webSearchProperties.getDefaultTopK();
        int safeConfiguredTopK = Math.max(1, tavilyProperties.getMaxResults());
        return Math.max(1, Math.min(safeRequestedTopK, safeConfiguredTopK));
    }

    private String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.replace("\r", "")
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String safeText(String text) {
        return text == null ? "" : text.trim();
    }

    private String truncate(String text, int maxChars) {
        return abbreviate(text, Math.max(1, maxChars));
    }

    private String abbreviateForLog(String text) {
        return abbreviate(text, Math.max(20, webSearchProperties.getLogQueryMaxChars()));
    }

    private String abbreviate(String text, int maxChars) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        if (maxChars <= 3) {
            return normalized.substring(0, maxChars);
        }
        return normalized.substring(0, maxChars - 3) + "...";
    }

    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TavilySearchRequest(
            String query,
            String topic,
            @JsonProperty("search_depth") String searchDepth,
            @JsonProperty("max_results") Integer maxResults,
            @JsonProperty("include_usage") Boolean includeUsage
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TavilySearchResponse(
            @JsonProperty("request_id") String requestId,
            @JsonProperty("response_time") Double responseTime,
            List<TavilySearchResult> results
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TavilySearchResult(
            String title,
            String url,
            String content,
            @JsonProperty("published_date") String publishedDate
    ) {
    }
}
