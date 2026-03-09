package com.ragadmin.server.document.dto;

public record ActivateDocumentVersionResponse(
        Long documentId,
        Long versionId,
        Integer currentVersion,
        String parseStatus
) {
}
