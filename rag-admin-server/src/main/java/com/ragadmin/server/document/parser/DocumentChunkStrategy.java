package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;

import java.util.List;

public interface DocumentChunkStrategy {

    boolean supports(ChunkContext context);

    List<ChunkDraft> chunk(List<Document> documents, ChunkContext context);
}
