package com.ragadmin.server.infra.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class WebSearchConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WebSearchConfiguration.class);

    @Bean
    public WebSearchProvider webSearchProvider(
            WebSearchProperties webSearchProperties,
            TavilyProperties tavilyProperties
    ) {
        if (!webSearchProperties.isEnabled()) {
            log.info("联网搜索已全局禁用，已回退 NoopWebSearchProvider");
            return new NoopWebSearchProvider();
        }
        if (tavilyProperties.isConfigured()) {
            log.info(
                    "联网搜索已启用 Tavily，baseUrl={}, timeoutSeconds={}, searchDepth={}, topic={}, maxResults={}",
                    tavilyProperties.getBaseUrl(),
                    tavilyProperties.getTimeoutSeconds(),
                    tavilyProperties.getSearchDepth(),
                    tavilyProperties.getTopic(),
                    tavilyProperties.getMaxResults()
            );
            return new TavilyWebSearchProvider(
                    TavilyApiSupport.buildRestClient(tavilyProperties),
                    tavilyProperties,
                    webSearchProperties
            );
        }
        log.info(
                "联网搜索未完成 Tavily 配置，已回退 NoopWebSearchProvider，tavilyEnabled={}, baseUrlConfigured={}, apiKeyConfigured={}",
                tavilyProperties.isEnabled(),
                StringUtils.hasText(tavilyProperties.getBaseUrl()),
                StringUtils.hasText(tavilyProperties.getApiKey())
        );
        return new NoopWebSearchProvider();
    }
}
