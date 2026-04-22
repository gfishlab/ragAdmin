package com.ragadmin.server.infra.elasticsearch;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "rag.search-engine.elasticsearch")
public class ElasticsearchProperties {
    private String uris;
    private boolean enabled = false;
}
