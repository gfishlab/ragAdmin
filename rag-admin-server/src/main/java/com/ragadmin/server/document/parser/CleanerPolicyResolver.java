package com.ragadmin.server.document.parser;

public interface CleanerPolicyResolver {

    DocumentCleanPolicy resolve(DocumentCleaningRequest request);
}
