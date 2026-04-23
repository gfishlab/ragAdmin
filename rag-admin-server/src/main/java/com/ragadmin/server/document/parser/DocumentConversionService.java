package com.ragadmin.server.document.parser;

public interface DocumentConversionService {

    boolean supportsConversion(String docType);

    byte[] convertToPdf(byte[] sourceContent, String sourceFileName) throws Exception;

    boolean isAvailable();
}
