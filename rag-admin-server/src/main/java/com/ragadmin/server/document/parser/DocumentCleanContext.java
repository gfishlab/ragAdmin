package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.entity.DocumentEntity;

public record DocumentCleanContext(
        DocumentEntity document,
        DocumentCleanPolicy policy
) {
}
