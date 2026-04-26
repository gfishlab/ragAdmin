package com.ragadmin.server.document.parser;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "rag.document.parse")
public class DocumentParseProperties {

    private int maxConcurrency = 4;
    private int dispatchBatchSize = 4;
    private int staleRunningTimeoutMinutes = 5;
    private int staleRecoveryBatchSize = 20;
    private int tokenEstimationDivisor = 4;
    private int errorMessageMaxLength = 500;
}
