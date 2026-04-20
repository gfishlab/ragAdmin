package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class DefaultDocumentCleaner implements DocumentCleaner {

    private final DocumentSignalAnalyzer signalAnalyzer;
    private final CleanerPolicyResolver cleanerPolicyResolver;
    private final List<DocumentCleanerStep> cleanerSteps;

    public DefaultDocumentCleaner(DocumentSignalAnalyzer signalAnalyzer,
                                  CleanerPolicyResolver cleanerPolicyResolver,
                                  List<DocumentCleanerStep> cleanerSteps) {
        this.signalAnalyzer = signalAnalyzer;
        this.cleanerPolicyResolver = cleanerPolicyResolver;
        this.cleanerSteps = cleanerSteps;
    }

    @Override
    public List<Document> clean(List<Document> documents, DocumentCleanContext context) {
        DocumentSignals signals = signalAnalyzer.analyze(documents, context);
        DocumentCleanPolicy resolvedPolicy = cleanerPolicyResolver.resolve(
                new DocumentCleaningRequest(context.document(), documents, signals)
        );
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
