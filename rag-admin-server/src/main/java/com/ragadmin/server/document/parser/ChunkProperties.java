package com.ragadmin.server.document.parser;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "rag.document.chunk")
public class ChunkProperties {

    private int defaultMaxChunkChars = 800;
    private int defaultOverlapChars = 120;
    private int defaultMinChunkChars = 50;

    private ContentTypeOverrides text = new ContentTypeOverrides();
    private ContentTypeOverrides table = new ContentTypeOverrides();
    private ContentTypeOverrides image = new ContentTypeOverrides();
    private ContentTypeOverrides mixed = new ContentTypeOverrides();

    @Data
    public static class ContentTypeOverrides {
        private Integer maxChunkChars;
        private Integer overlapChars;
        private Integer minChunkChars;
    }

    public ChunkStrategyProperties resolve(String contentType) {
        ContentTypeOverrides overrides = switch (contentType) {
            case "TABLE" -> table;
            case "IMAGE" -> image;
            case "MIXED" -> mixed;
            default -> text;
        };
        return new ChunkStrategyProperties(
                overrides.getMaxChunkChars() != null ? overrides.getMaxChunkChars() : defaultMaxChunkChars,
                overrides.getOverlapChars() != null ? overrides.getOverlapChars() : defaultOverlapChars,
                overrides.getMinChunkChars() != null ? overrides.getMinChunkChars() : defaultMinChunkChars
        );
    }
}
