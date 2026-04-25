package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Order(4)
public class ContentAwareChunkStrategy implements DocumentChunkStrategy {

    private static final String TABLE = "TABLE";
    private static final String IMAGE = "IMAGE";
    private static final String HEADING = "HEADING";
    private static final String TEXT = "TEXT";

    @Override
    public boolean supports(ChunkContext context) {
        return context.contentContainsTable() || context.contentContainsImage();
    }

    @Override
    public List<ChunkDraft> chunk(List<Document> documents, ChunkContext context) {
        ChunkStrategyProperties props = context.properties();
        String fullText = documents.stream()
                .filter(Objects::nonNull)
                .map(Document::getText)
                .filter(Objects::nonNull)
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n\n" + b);

        if (fullText.isBlank()) return List.of();

        List<ContentBlock> blocks = identifyBlocks(fullText);
        List<ChunkDraft> chunks = aggregateBlocks(blocks, props, context.contentType());

        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> meta = new HashMap<>(chunks.get(i).metadata());
            meta.put("chunkIndex", i);
            meta.put("totalChunks", chunks.size());
            chunks.set(i, new ChunkDraft(chunks.get(i).text(), meta, chunks.get(i).parentChunkId()));
        }
        return chunks;
    }

    List<ContentBlock> identifyBlocks(String markdown) {
        List<ContentBlock> blocks = new ArrayList<>();
        String[] lines = markdown.split("\n");
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                i++;
                continue;
            }

            if (isPipeTableRow(trimmed)) {
                int start = i;
                while (i < lines.length && isPipeTableRow(lines[i].trim())) {
                    i++;
                }
                blocks.add(new ContentBlock(TABLE, lines, start, i));
            } else if (trimmed.startsWith("#") && trimmed.matches("^#{1,6}\\s+.+")) {
                blocks.add(new ContentBlock(HEADING, lines, i, i + 1));
                i++;
            } else if (trimmed.matches("!\\[[^\\]]*\\]\\([^)]+\\).*")) {
                blocks.add(new ContentBlock(IMAGE, lines, i, i + 1));
                i++;
            } else {
                int start = i;
                while (i < lines.length && !lines[i].trim().isEmpty()
                        && !isPipeTableRow(lines[i].trim())
                        && !lines[i].trim().startsWith("#")
                        && !lines[i].trim().matches("!\\[[^\\]]*\\]\\([^)]+\\).*")) {
                    i++;
                }
                if (i > start) {
                    blocks.add(new ContentBlock(TEXT, lines, start, i));
                } else {
                    i++;
                }
            }
        }
        return blocks;
    }

    private List<ChunkDraft> aggregateBlocks(List<ContentBlock> blocks, ChunkStrategyProperties props, String contentType) {
        int maxChars = props.maxChunkCharsSafe();
        int overlapChars = props.overlapCharsSafe();

        List<ChunkDraft> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean currentHasTable = false;
        boolean currentHasImage = false;

        for (ContentBlock block : blocks) {
            String blockText = block.text();
            boolean isTable = TABLE.equals(block.type);
            boolean isImage = IMAGE.equals(block.type);

            if (!current.isEmpty() && current.length() + blockText.length() + 2 > maxChars) {
                if (isTable && !currentHasTable && !currentHasImage) {
                    // Table coming up, finalize current chunk and handle table separately
                    chunks.add(buildDraft(current.toString(), currentHasTable, currentHasImage, contentType));
                    current = new StringBuilder(overlapTail(current.toString(), overlapChars));
                    currentHasTable = false;
                    currentHasImage = false;
                } else if (isImage) {
                    // Image block: finalize current and start fresh with image context
                    chunks.add(buildDraft(current.toString(), currentHasTable, currentHasImage, contentType));
                    current = new StringBuilder(overlapTail(current.toString(), overlapChars));
                    currentHasTable = false;
                    currentHasImage = false;
                } else if (isTable && currentHasTable) {
                    // Consecutive tables: split the large table
                    chunks.addAll(splitLargeTable(blockText, maxChars, overlapChars, contentType));
                    continue;
                } else {
                    chunks.add(buildDraft(current.toString(), currentHasTable, currentHasImage, contentType));
                    current = new StringBuilder(overlapTail(current.toString(), overlapChars));
                    currentHasTable = false;
                    currentHasImage = false;
                }
            }

            // Handle oversized single table
            if (isTable && blockText.length() > maxChars && current.isEmpty()) {
                chunks.addAll(splitLargeTable(blockText, maxChars, overlapChars, contentType));
                continue;
            }

            if (!current.isEmpty()) current.append("\n\n");
            current.append(blockText);
            if (isTable) currentHasTable = true;
            if (isImage) currentHasImage = true;
        }

        if (!current.isEmpty()) {
            chunks.add(buildDraft(current.toString(), currentHasTable, currentHasImage, contentType));
        }

        return chunks;
    }

    private List<ChunkDraft> splitLargeTable(String tableText, int maxChars, int overlapChars, String contentType) {
        List<ChunkDraft> result = new ArrayList<>();
        String[] rows = tableText.split("\n");

        if (rows.length < 3) {
            // Header + separator + at most 1 data row, can't split further
            result.add(buildDraft(tableText, true, false, contentType));
            return result;
        }

        String header = rows[0];
        String separator = rows[1];
        String headerBlock = header + "\n" + separator;

        StringBuilder chunk = new StringBuilder(headerBlock);
        for (int i = 2; i < rows.length; i++) {
            if (chunk.length() + rows[i].length() + 1 > maxChars && chunk.length() > headerBlock.length()) {
                result.add(buildDraft(chunk.toString(), true, false, contentType));
                chunk = new StringBuilder(headerBlock);
            }
            chunk.append("\n").append(rows[i]);
        }
        if (chunk.length() > headerBlock.length()) {
            result.add(buildDraft(chunk.toString(), true, false, contentType));
        }
        return result;
    }

    private ChunkDraft buildDraft(String text, boolean hasTable, boolean hasImage, String contentType) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("containsTable", hasTable);
        meta.put("containsImage", hasImage);
        meta.put("contentType", contentType != null ? contentType : "TEXT");
        return new ChunkDraft(text.trim(), meta);
    }

    private String overlapTail(String text, int overlapChars) {
        if (text.length() <= overlapChars) return text;
        String tail = text.substring(text.length() - overlapChars);
        int paraBreak = tail.indexOf("\n\n");
        if (paraBreak >= 0) return tail.substring(paraBreak + 2);
        int lineBreak = tail.indexOf('\n');
        if (lineBreak >= 0) return tail.substring(lineBreak + 1);
        int space = tail.indexOf(' ');
        if (space >= 0) return tail.substring(space + 1);
        return tail;
    }

    private boolean isPipeTableRow(String line) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("|") || !trimmed.endsWith("|")) return false;
        // Separator row: |---|---|
        if (trimmed.matches("^\\|[-:]+[-| :]*\\|$")) return true;
        // Data row: | cell | cell |
        if (trimmed.length() >= 3) {
            long pipeCount = trimmed.chars().filter(c -> c == '|').count();
            return pipeCount >= 2;
        }
        return false;
    }

    record ContentBlock(String type, String[] sourceLines, int startLine, int endLine) {
        String text() {
            StringBuilder sb = new StringBuilder();
            for (int i = startLine; i < endLine; i++) {
                if (i > startLine) sb.append("\n");
                sb.append(sourceLines[i]);
            }
            return sb.toString();
        }
    }
}
