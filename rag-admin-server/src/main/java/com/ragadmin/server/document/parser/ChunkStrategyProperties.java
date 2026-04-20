package com.ragadmin.server.document.parser;

public record ChunkStrategyProperties(
        int maxChunkChars,
        int overlapChars,
        int minChunkChars
) {

    public static ChunkStrategyProperties defaults() {
        return new ChunkStrategyProperties(800, 120, 50);
    }

    public int maxChunkCharsSafe() {
        return Math.max(100, maxChunkChars);
    }

    public int overlapCharsSafe() {
        return Math.max(0, Math.min(overlapChars, maxChunkCharsSafe() / 2));
    }

    public int minChunkCharsSafe() {
        return Math.max(0, minChunkChars);
    }
}
