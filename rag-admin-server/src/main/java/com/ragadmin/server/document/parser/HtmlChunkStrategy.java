package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(12)
public class HtmlChunkStrategy implements DocumentChunkStrategy {

    private final RecursiveFallbackStrategy fallback = new RecursiveFallbackStrategy();

    @Override
    public boolean supports(ChunkContext context) {
        return context.isHtml();
    }

    @Override
    public List<ChunkDraft> chunk(List<Document> documents, ChunkContext context) {
        ChunkStrategyProperties props = context.properties();
        List<ChunkDraft> result = new ArrayList<>();
        for (Document document : documents) {
            String text = normalizeText(document.getText());
            if (text.isEmpty()) {
                continue;
            }
            List<String> chunkTexts = fallback.splitText(text, props);
            for (int i = 0; i < chunkTexts.size(); i++) {
                Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
                metadata.put("parentDocumentId", document.getId());
                metadata.put("chunkIndex", i);
                metadata.put("totalChunks", chunkTexts.size());
                result.add(new ChunkDraft(chunkTexts.get(i), metadata));
            }
        }
        return result;
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").trim();
    }
}
