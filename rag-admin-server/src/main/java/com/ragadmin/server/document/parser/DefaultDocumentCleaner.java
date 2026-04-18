package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class DefaultDocumentCleaner implements DocumentCleaner {

    private final CleanerPolicyResolver cleanerPolicyResolver;
    private final List<DocumentCleanerStep> cleanerSteps;

    public DefaultDocumentCleaner(CleanerPolicyResolver cleanerPolicyResolver, List<DocumentCleanerStep> cleanerSteps) {
        this.cleanerPolicyResolver = cleanerPolicyResolver;
        this.cleanerSteps = cleanerSteps;
    }

    @Override
    public List<Document> clean(List<Document> documents, DocumentCleanContext context) {
        DocumentCleanPolicy resolvedPolicy = cleanerPolicyResolver.resolve(new DocumentCleaningRequest(context.document(), documents));
        DocumentCleanContext resolvedContext = new DocumentCleanContext(context.document(), resolvedPolicy);
        List<Document> current = documents;
        for (DocumentCleanerStep cleanerStep : cleanerSteps) {
            if (!cleanerStep.supports(resolvedContext)) {
                continue;
            }
            current = cleanerStep.clean(current, resolvedContext);
        }
        return current;
    }
}
