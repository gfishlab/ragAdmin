package com.ragadmin.server.document.parser;

public record DocumentCleanPolicy(
        boolean safeCleanEnabled,
        boolean semanticCleanEnabled,
        boolean preserveSymbols,
        boolean headerFooterCleanEnabled,
        boolean lineMergeEnabled,
        boolean ocrNoiseCleanEnabled
) {
}
