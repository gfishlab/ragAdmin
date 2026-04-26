package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(50)
public class RecursiveFallbackStrategy implements DocumentChunkStrategy {

    private final TableDetectionProperties tableDetectionProperties;

    public RecursiveFallbackStrategy() {
        this.tableDetectionProperties = new TableDetectionProperties();
    }

    public RecursiveFallbackStrategy(TableDetectionProperties tableDetectionProperties) {
        this.tableDetectionProperties = tableDetectionProperties;
    }

    @Override
    public boolean supports(ChunkContext context) {
        return true;
    }

    @Override
    public List<ChunkDraft> chunk(List<Document> documents, ChunkContext context) {
        ChunkStrategyProperties props = context.properties();
        List<ChunkDraft> result = new ArrayList<>();
        for (Document document : documents) {
            String normalized = document.getText() == null ? "" : document.getText().replace("\r\n", "\n").trim();
            if (normalized.isEmpty()) {
                continue;
            }
            List<String> chunkTexts = splitText(normalized, props);
            for (int i = 0; i < chunkTexts.size(); i++) {
                java.util.Map<String, Object> metadata = new java.util.LinkedHashMap<>(document.getMetadata());
                metadata.put("parentDocumentId", document.getId());
                metadata.put("chunkIndex", i);
                metadata.put("totalChunks", chunkTexts.size());
                result.add(new ChunkDraft(chunkTexts.get(i), metadata));
            }
        }
        return result;
    }

    List<String> splitText(String normalized, ChunkStrategyProperties props) {
        int maxChunkChars = props.maxChunkCharsSafe();
        List<String> chunks = new ArrayList<>();
        List<String> paragraphs = List.of(normalized.split("\\n\\s*\\n"));
        StringBuilder current = new StringBuilder();
        int chunkNo = 0;
        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (current.length() > 0 && current.length() + trimmed.length() + 2 > maxChunkChars) {
                chunks.add(current.toString());
                chunkNo++;
                current = new StringBuilder(overlapTail(chunks.get(chunkNo - 1), props.overlapCharsSafe()));
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(trimmed);
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }

        if (chunks.isEmpty()) {
            chunks.add(normalized.substring(0, Math.min(normalized.length(), maxChunkChars)));
        }
        return chunks;
    }

    String overlapTail(String source, int tailLength) {
        if (source.length() <= tailLength) {
            return source;
        }
        int rawStart = source.length() - tailLength;
        int tolerance = Math.max(tableDetectionProperties.getOverlapTailMinChars(), (int) (tailLength * tableDetectionProperties.getOverlapTailRatio()));
        int searchFrom = Math.max(0, rawStart - tolerance);

        for (int i = rawStart; i > searchFrom; i--) {
            if (source.charAt(i) == '\n' && source.charAt(i - 1) == '\n') {
                return source.substring(i + 1);
            }
        }
        for (int i = rawStart; i > searchFrom; i--) {
            if (source.charAt(i) == '\n') {
                return source.substring(i + 1);
            }
        }
        for (int i = rawStart; i > searchFrom; i--) {
            if (Character.isWhitespace(source.charAt(i))) {
                return source.substring(i + 1);
            }
        }
        return source.substring(rawStart);
    }
}
