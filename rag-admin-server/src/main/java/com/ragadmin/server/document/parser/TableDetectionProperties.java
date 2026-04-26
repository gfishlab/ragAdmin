package com.ragadmin.server.document.parser;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "rag.document.table-detection")
public class TableDetectionProperties {

    private int minTableRows = 2;
    private double tableRowRatio = 0.5;
    private int minTabCount = 2;
    private int minPipeCount = 3;
    private double overlapTailRatio = 0.3;
    private int overlapTailMinChars = 10;
}
