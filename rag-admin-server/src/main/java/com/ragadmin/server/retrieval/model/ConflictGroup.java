package com.ragadmin.server.retrieval.model;

import java.util.List;

public class ConflictGroup {

    private String groupId;
    private ConflictType conflictType;
    private double confidence;
    private Long preferredChunkId;
    private String reason;
    private List<Long> chunkIds;

    public ConflictGroup() {}

    public ConflictGroup(String groupId, ConflictType conflictType, double confidence,
                         Long preferredChunkId, String reason, List<Long> chunkIds) {
        this.groupId = groupId;
        this.conflictType = conflictType;
        this.confidence = confidence;
        this.preferredChunkId = preferredChunkId;
        this.reason = reason;
        this.chunkIds = chunkIds;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public ConflictType getConflictType() {
        return conflictType;
    }

    public void setConflictType(ConflictType conflictType) {
        this.conflictType = conflictType;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public Long getPreferredChunkId() {
        return preferredChunkId;
    }

    public void setPreferredChunkId(Long preferredChunkId) {
        this.preferredChunkId = preferredChunkId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<Long> getChunkIds() {
        return chunkIds;
    }

    public void setChunkIds(List<Long> chunkIds) {
        this.chunkIds = chunkIds;
    }
}
