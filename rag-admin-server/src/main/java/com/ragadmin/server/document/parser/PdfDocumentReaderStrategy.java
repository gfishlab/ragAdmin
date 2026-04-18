package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@Order(40)
public class PdfDocumentReaderStrategy implements DocumentReaderStrategy {

    private final DocumentMetadataFactory documentMetadataFactory;
    private final MineruParseService mineruParseService;

    public PdfDocumentReaderStrategy(DocumentMetadataFactory documentMetadataFactory, MineruParseService mineruParseService) {
        this.documentMetadataFactory = documentMetadataFactory;
        this.mineruParseService = mineruParseService;
    }

    @Override
    public boolean supports(DocumentParseRequest request) {
        return "PDF".equals(request.docType());
    }

    @Override
    public List<Document> read(DocumentParseRequest request) {
        List<Document> paragraphDocuments = readWithParagraphReader(request);
        if (hasTextDocuments(paragraphDocuments)) {
            return documentMetadataFactory.enrichDocuments(paragraphDocuments, request, "PDF_PARAGRAPH_READER", "TEXT");
        }
        List<Document> pageDocuments = readWithPageReader(request);
        if (hasTextDocuments(pageDocuments)) {
            return documentMetadataFactory.enrichDocuments(pageDocuments, request, "PDF_PAGE_READER", "TEXT");
        }
        try {
            return documentMetadataFactory.enrichDocuments(mineruParseService.parse(request), request, "MINERU_API", "OCR");
        } catch (Exception ex) {
            throw new IllegalStateException("PDF 解析失败，按页读取和 MinerU 均未成功", ex);
        }
    }

    protected List<Document> readWithParagraphReader(DocumentParseRequest request) {
        ParagraphPdfDocumentReader reader = new ParagraphPdfDocumentReader(new ByteArrayResource(request.content(), request.document().getDocName()));
        return reader.get();
    }

    protected List<Document> readWithPageReader(DocumentParseRequest request) {
        PagePdfDocumentReader reader = new PagePdfDocumentReader(new ByteArrayResource(request.content(), request.document().getDocName()));
        return reader.get();
    }

    private boolean hasTextDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return false;
        }
        return documents.stream().anyMatch(document -> StringUtils.hasText(document.getText()));
    }
}
