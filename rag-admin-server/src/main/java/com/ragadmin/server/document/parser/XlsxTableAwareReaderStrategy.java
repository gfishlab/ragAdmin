package com.ragadmin.server.document.parser;

import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Order(35)
public class XlsxTableAwareReaderStrategy implements DocumentReaderStrategy {

    private static final Logger log = LoggerFactory.getLogger(XlsxTableAwareReaderStrategy.class);

    private final DocumentMetadataFactory documentMetadataFactory;

    public XlsxTableAwareReaderStrategy(DocumentMetadataFactory documentMetadataFactory) {
        this.documentMetadataFactory = documentMetadataFactory;
    }

    @Override
    public boolean supports(DocumentParseRequest request) {
        return "XLSX".equals(request.docType());
    }

    @Override
    public List<Document> read(DocumentParseRequest request) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(request.content()))) {
            List<Document> documents = new ArrayList<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String markdownTable = sheetToMarkdownTable(sheet);
                if (!markdownTable.isBlank()) {
                    Document doc = new Document(markdownTable, Map.of(
                            "sheetName", sheet.getSheetName(),
                            "sheetIndex", i
                    ));
                    documents.add(doc);
                }
            }
            if (documents.isEmpty()) {
                log.warn("XLSX 文件所有 Sheet 均为空，documentName={}", request.document().getDocName());
                documents.add(new Document("", Map.of("sheetName", "empty", "sheetIndex", 0)));
            }
            return documentMetadataFactory.enrichDocuments(documents, request, "XLSX_TABLE_AWARE", "TEXT");
        }
    }

    String sheetToMarkdownTable(Sheet sheet) {
        if (sheet.getPhysicalNumberOfRows() == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(sheet.getSheetName()).append("\n\n");

        boolean headerWritten = false;
        for (Row row : sheet) {
            if (row == null) continue;

            List<String> cells = new ArrayList<>();
            boolean hasContent = false;
            for (int c = 0; c < row.getLastCellNum(); c++) {
                Cell cell = row.getCell(c);
                String value = formatCell(cell);
                cells.add(value);
                if (!value.isBlank()) hasContent = true;
            }

            if (!hasContent) continue;

            sb.append("| ").append(String.join(" | ", cells)).append(" |\n");

            if (!headerWritten) {
                sb.append("|").repeat(" --- |", cells.size()).append("\n");
                headerWritten = true;
            }
        }

        return sb.toString();
    }

    private String formatCell(Cell cell) {
        if (cell == null) return " ";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                }
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    yield String.valueOf((long) d);
                }
                yield String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (Exception e) {
                    try {
                        yield String.valueOf(cell.getNumericCellValue());
                    } catch (Exception ex) {
                        yield cell.getCellFormula();
                    }
                }
            }
            default -> " ";
        };
    }
}
