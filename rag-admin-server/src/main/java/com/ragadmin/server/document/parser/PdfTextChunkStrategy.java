package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(22)
public class PdfTextChunkStrategy implements DocumentChunkStrategy {

    private final RecursiveFallbackStrategy fallback = new RecursiveFallbackStrategy();

    @Override
    public boolean supports(ChunkContext context) {
        return context.isPdf() && context.isTextMode();
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
            List<String> paragraphs = splitPreservingTables(text, props.maxChunkCharsSafe());
            List<String> chunkTexts = aggregateParagraphs(paragraphs, props);
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

    List<String> splitPreservingTables(String text, int maxChunkChars) {
        List<String> paragraphs = new ArrayList<>();
        String[] rawParagraphs = text.split("\\n\\s*\\n");
        for (String para : rawParagraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (isTableBlock(trimmed) && trimmed.length() <= maxChunkChars) {
                paragraphs.add(trimmed);
            } else {
                paragraphs.add(trimmed);
            }
        }
        return paragraphs;
    }

    boolean isTableBlock(String text) {
        String[] lines = text.split("\n");
        if (lines.length < 2) {
            return false;
        }
        int tableRows = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (isTabSeparatedRow(trimmed) || isPipeSeparatedRow(trimmed)) {
                tableRows++;
            }
        }
        return tableRows >= 2 && tableRows >= lines.length * 0.5;
    }

    private boolean isTabSeparatedRow(String line) {
        long tabCount = line.chars().filter(c -> c == '\t').count();
        return tabCount >= 2;
    }

    private boolean isPipeSeparatedRow(String line) {
        if (!line.startsWith("|") || !line.endsWith("|")) {
            return false;
        }
        long pipeCount = line.chars().filter(c -> c == '|').count();
        return pipeCount >= 3;
    }

    private List<String> aggregateParagraphs(List<String> paragraphs, ChunkStrategyProperties props) {
        int maxChars = props.maxChunkCharsSafe();
        int overlapChars = props.overlapCharsSafe();
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int chunkNo = 0;

        for (String paragraph : paragraphs) {
            if (current.length() > 0 && current.length() + paragraph.length() + 2 > maxChars) {
                chunks.add(current.toString());
                chunkNo++;
                current = new StringBuilder(fallback.overlapTail(chunks.get(chunkNo - 1), overlapChars));
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(paragraph);
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        if (chunks.isEmpty()) {
            chunks.add(paragraphs.getFirst().substring(0, Math.min(paragraphs.getFirst().length(), maxChars)));
        }
        return chunks;
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").trim();
    }
}
