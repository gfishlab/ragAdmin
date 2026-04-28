package com.ragadmin.server.retrieval.model;

import com.ragadmin.server.retrieval.service.RetrievalService.RetrievedChunk;

import java.util.List;

public class ConflictDetectionResult {

    private final List<ConflictGroup> conflicts;
    private final List<RetrievedChunk> resolvedChunks;

    public ConflictDetectionResult(List<ConflictGroup> conflicts, List<RetrievedChunk> resolvedChunks) {
        this.conflicts = conflicts;
        this.resolvedChunks = resolvedChunks;
    }

    public List<ConflictGroup> getConflicts() {
        return conflicts;
    }

    public List<RetrievedChunk> getResolvedChunks() {
        return resolvedChunks;
    }

    public boolean hasConflicts() {
        return conflicts != null && !conflicts.isEmpty();
    }
}
