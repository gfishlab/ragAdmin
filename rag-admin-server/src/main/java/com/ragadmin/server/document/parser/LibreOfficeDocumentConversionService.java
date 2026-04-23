package com.ragadmin.server.document.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

@Component
public class LibreOfficeDocumentConversionService implements DocumentConversionService {

    private static final Logger log = LoggerFactory.getLogger(LibreOfficeDocumentConversionService.class);
    private static final Set<String> CONVERTIBLE_TYPES = Set.of("DOCX", "PPTX");

    private final ConversionProperties conversionProperties;
    private volatile Boolean availableCache;

    public LibreOfficeDocumentConversionService(ConversionProperties conversionProperties) {
        this.conversionProperties = conversionProperties;
    }

    @Override
    public boolean supportsConversion(String docType) {
        return CONVERTIBLE_TYPES.contains(docType);
    }

    @Override
    public boolean isAvailable() {
        if (!conversionProperties.isEnabled()) {
            return false;
        }
        if (availableCache == null) {
            availableCache = checkLibreOfficeAvailable();
        }
        return availableCache;
    }

    @Override
    public byte[] convertToPdf(byte[] sourceContent, String sourceFileName) throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("LibreOffice 不可用，无法执行文档转换");
        }

        String extension = extractExtension(sourceFileName);
        Path tempDir = Files.createTempDirectory("lo-convert-");
        Path inputFile = tempDir.resolve("source." + extension);
        Path outputFile = tempDir.resolve("source.pdf");

        try {
            Files.write(inputFile, sourceContent);

            List<String> command = List.of(
                    conversionProperties.getLibreOfficePath(),
                    "--headless",
                    "--convert-to", "pdf",
                    "--outdir", tempDir.toString(),
                    inputFile.toString()
            );

            ProcessBuilder pb = new ProcessBuilder(command)
                    .redirectErrorStream(true);
            Process process = pb.start();

            String output = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(
                    conversionProperties.getTimeoutSeconds(),
                    java.util.concurrent.TimeUnit.SECONDS
            );

            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("LibreOffice 转换超时（" + conversionProperties.getTimeoutSeconds() + "s）");
            }

            if (process.exitValue() != 0) {
                throw new RuntimeException("LibreOffice 转换失败，退出码=" + process.exitValue() + "，输出=" + truncate(output, 500));
            }

            if (!Files.exists(outputFile)) {
                throw new RuntimeException("LibreOffice 转换后未生成 PDF 文件，输出=" + truncate(output, 500));
            }

            return Files.readAllBytes(outputFile);
        } finally {
            deleteQuietly(inputFile);
            deleteQuietly(outputFile);
            deleteQuietly(tempDir);
        }
    }

    private boolean checkLibreOfficeAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    conversionProperties.getLibreOfficePath(), "--version"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                log.info("检测到 LibreOffice: {}", output.trim());
                return true;
            }
            return false;
        } catch (Exception e) {
            log.debug("LibreOffice 不可用: {}", e.getMessage());
            return false;
        }
    }

    private String extractExtension(String fileName) {
        if (fileName == null) return "docx";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return "docx";
    }

    private void deleteQuietly(Path path) {
        try {
            if (path != null) Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private String truncate(String value, int maxLen) {
        if (value == null) return "";
        return value.length() > maxLen ? value.substring(0, maxLen) : value;
    }
}
