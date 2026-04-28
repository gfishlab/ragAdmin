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
    private static final String TEXT = "TEXT";

    private final TableDetectionProperties tableDetectionProperties;

    public ContentAwareChunkStrategy(TableDetectionProperties tableDetectionProperties) {
        this.tableDetectionProperties = tableDetectionProperties;
    }

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

        // Phase 1: split by headings into sections
        List<String> sections = splitByHeadings(fullText);

        // Phase 2: within each section, identify blocks and aggregate
        List<ChunkDraft> allDrafts = new ArrayList<>();
        for (String section : sections) {
            if (section.isBlank()) continue;
            List<ContentBlock> blocks = identifyBlocks(section);
            allDrafts.addAll(aggregateBlocks(blocks, props, context.contentType()));
        }

        // Finalize metadata
        for (int i = 0; i < allDrafts.size(); i++) {
            Map<String, Object> meta = new HashMap<>(allDrafts.get(i).metadata());
            meta.put("chunkIndex", i);
            meta.put("totalChunks", allDrafts.size());
            allDrafts.set(i, new ChunkDraft(allDrafts.get(i).text(), meta, allDrafts.get(i).parentChunkId()));
        }
        return allDrafts;
    }

    List<String> splitByHeadings(String text) {
        List<String> sections = new ArrayList<>();
        String[] lines = text.split("\n");
        StringBuilder current = new StringBuilder();
        boolean inCodeBlock = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                current.append(line).append("\n");
                continue;
            }

            if (!inCodeBlock && trimmed.matches("^#{1,6}\\s+.+") && current.length() > 0) {
                String section = current.toString().trim();
                if (!section.isEmpty()) {
                    sections.add(section);
                }
                current = new StringBuilder();
            }
            current.append(line).append("\n");
        }

        String last = current.toString().trim();
        if (!last.isEmpty()) {
            sections.add(last);
        }
        return sections;
    }

    List<ContentBlock> identifyBlocks(String markdown) {
        List<ContentBlock> blocks = new ArrayList<>();
        String[] lines = markdown.split("\n");
        int i = 0;
        boolean inCodeBlock = false;

        while (i < lines.length) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                i++;
                continue;
            }

            // Track code block state
            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                int start = i;
                i++;
                // Accumulate the entire code block as a single TEXT block
                while (i < lines.length) {
                    if (lines[i].trim().startsWith("```")) {
                        i++;
                        inCodeBlock = !inCodeBlock;
                        break;
                    }
                    i++;
                }
                blocks.add(new ContentBlock(TEXT, lines, start, i));
                continue;
            }

            // Inside code block, treat as TEXT
            if (inCodeBlock) {
                int start = i;
                while (i < lines.length && !lines[i].trim().startsWith("```")) {
                    i++;
                }
                if (i > start) {
                    blocks.add(new ContentBlock(TEXT, lines, start, i));
                }
                continue;
            }

            if (isPipeTableRow(trimmed)) {
                int start = i;
                while (i < lines.length && isPipeTableRow(lines[i].trim())) {
                    i++;
                }
                blocks.add(new ContentBlock(TABLE, lines, start, i));
            } else if (trimmed.matches("!\\[[^\\]]*\\]\\([^)]+\\).*")) {
                blocks.add(new ContentBlock(IMAGE, lines, i, i + 1));
                i++;
            } else {
                int start = i;
                while (i < lines.length && !lines[i].trim().isEmpty()
                        && !isPipeTableRow(lines[i].trim())
                        && !lines[i].trim().startsWith("```")
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
                    chunks.add(buildDraft(current.toString(), currentHasTable, currentHasImage, contentType));
                    current = new StringBuilder(overlapTail(current.toString(), overlapChars));
                    currentHasTable = false;
                    currentHasImage = false;
                } else if (isImage) {
                    chunks.add(buildDraft(current.toString(), currentHasTable, currentHasImage, contentType));
                    current = new StringBuilder();
                    currentHasTable = false;
                    currentHasImage = false;
                } else if (isTable && currentHasTable) {
                    chunks.addAll(splitLargeTable(blockText, maxChars, contentType));
                    continue;
                } else {
                    chunks.add(buildDraft(current.toString(), currentHasTable, currentHasImage, contentType));
                    current = new StringBuilder(overlapTail(current.toString(), overlapChars));
                    currentHasTable = false;
                    currentHasImage = false;
                }
            }

            if (isTable && blockText.length() > maxChars && current.isEmpty()) {
                chunks.addAll(splitLargeTable(blockText, maxChars, contentType));
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

    private List<ChunkDraft> splitLargeTable(String tableText, int maxChars, String contentType) {
        List<ChunkDraft> result = new ArrayList<>();
        String[] rows = tableText.split("\n");

        if (rows.length < 3) {
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
        meta.put("chunkStrategy", "CONTENT_AWARE");
        meta.put("containsTable", hasTable);
        meta.put("containsImage", hasImage);
        meta.put("contentType", contentType != null ? contentType : "TEXT");
        return new ChunkDraft(text.trim(), meta);
    }

    String overlapTail(String source, int tailLength) {
        if (source.length() <= tailLength) return source;
        int rawStart = source.length() - tailLength;
        int tolerance = Math.max(tableDetectionProperties.getOverlapTailMinChars(),
                (int) (tailLength * tableDetectionProperties.getOverlapTailRatio()));
        int searchFrom = Math.max(0, rawStart - tolerance);

        for (int i = rawStart; i > searchFrom; i--) {
            if (source.charAt(i) == '\n' && i > 0 && source.charAt(i - 1) == '\n') {
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

    boolean isPipeTableRow(String line) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("|") || !trimmed.endsWith("|")) return false;
        if (trimmed.matches("^\\|[-:]+[-| :]*\\|$")) return true;
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
