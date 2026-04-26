package com.ragadmin.server.document.parser;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "rag.document.signal")
public class SignalAnalysisProperties {

    private double headerFooterThreshold = 0.6;
    private double blankLineRatioThreshold = 0.4;
    private double symbolDensityThreshold = 0.15;
    private double weakParagraphAvgLines = 2.0;
    private double tableRatioThreshold = 0.1;
    private double imageRatioThreshold = 0.05;
}
