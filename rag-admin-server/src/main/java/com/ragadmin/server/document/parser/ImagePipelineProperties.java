package com.ragadmin.server.document.parser;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "rag.document.image-pipeline")
public class ImagePipelineProperties {

    private int concurrency = 10;
    private long maxImageSize = 10 * 1024 * 1024;
    private int presignedUrlExpirySeconds = 1800;
}
