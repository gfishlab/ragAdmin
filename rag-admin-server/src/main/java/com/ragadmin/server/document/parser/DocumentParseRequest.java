package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.entity.DocumentVersionEntity;

public record DocumentParseRequest(
        DocumentEntity document,
        DocumentVersionEntity version,
        byte[] content,
        String docType
) {
}
