package com.ragadmin.server.document.parser;

import java.util.Map;

public record ChunkDraft(String text, Map<String, Object> metadata) {}
