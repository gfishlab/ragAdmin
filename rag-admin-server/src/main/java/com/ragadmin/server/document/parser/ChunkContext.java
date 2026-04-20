package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.entity.DocumentEntity;

public record ChunkContext(
        DocumentEntity document,
        DocumentSignals signals,
        ChunkStrategyProperties properties
) {}
