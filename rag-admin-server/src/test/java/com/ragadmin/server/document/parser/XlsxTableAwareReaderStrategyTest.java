package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.entity.DocumentVersionEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class XlsxTableAwareReaderStrategyTest {

    private final XlsxTableAwareReaderStrategy strategy = new XlsxTableAwareReaderStrategy(new DocumentMetadataFactory());

    @Test
    void shouldSupportXlsxOnly() {
        assertTrue(strategy.supports(request("XLSX")));
        assertFalse(strategy.supports(request("DOCX")));
        assertFalse(strategy.supports(request("PDF")));
        assertFalse(strategy.supports(request("XLS")));
    }

    @Test
    void shouldConvertSheetToMarkdownTable() throws Exception {
        var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        var sheet = workbook.createSheet("销售数据");
        var header = sheet.createRow(0);
        header.createCell(0).setCellValue("产品");
        header.createCell(1).setCellValue("数量");
        header.createCell(2).setCellValue("单价");
        var row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("苹果");
        row1.createCell(1).setCellValue(100);
        row1.createCell(2).setCellValue(3.5);

        String md = strategy.sheetToMarkdownTable(sheet);

        assertTrue(md.contains("## 销售数据"));
        assertTrue(md.contains("| 产品 | 数量 | 单价 |"));
        assertTrue(md.contains("| --- | --- | --- |"));
        assertTrue(md.contains("| 苹果 | 100 | 3.5 |"));
        workbook.close();
    }

    @Test
    void shouldHandleEmptySheet() throws Exception {
        var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        var sheet = workbook.createSheet("空Sheet");

        String md = strategy.sheetToMarkdownTable(sheet);

        assertTrue(md.isEmpty());
        workbook.close();
    }

    @Test
    void shouldHandleMultipleSheets() throws Exception {
        var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        var sheet1 = workbook.createSheet("Sheet1");
        sheet1.createRow(0).createCell(0).setCellValue("A1");
        sheet1.getRow(0).createCell(1).setCellValue("B1");
        var sheet2 = workbook.createSheet("Sheet2");
        sheet2.createRow(0).createCell(0).setCellValue("C1");
        sheet2.getRow(0).createCell(1).setCellValue("D1");

        var baos = new java.io.ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();

        var documents = strategy.read(request(baos.toByteArray()));

        assertEquals(2, documents.size());
        assertEquals("Sheet1", documents.get(0).getMetadata().get("sheetName"));
        assertEquals("Sheet2", documents.get(1).getMetadata().get("sheetName"));
        assertTrue(documents.get(0).getText().contains("A1"));
        assertTrue(documents.get(1).getText().contains("C1"));
    }

    private DocumentParseRequest request(String docType) {
        return request(new byte[0], docType);
    }

    private DocumentParseRequest request(byte[] content) {
        return request(content, "XLSX");
    }

    private DocumentParseRequest request(byte[] content, String docType) {
        DocumentEntity document = new DocumentEntity();
        document.setDocType(docType);
        document.setDocName("test." + docType.toLowerCase());
        DocumentVersionEntity version = new DocumentVersionEntity();
        version.setStorageBucket("bucket");
        version.setStorageObjectKey("object");
        return new DocumentParseRequest(document, version, content, docType);
    }
}
