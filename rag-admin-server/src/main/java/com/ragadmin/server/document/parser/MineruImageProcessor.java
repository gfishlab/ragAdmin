package com.ragadmin.server.document.parser;

import com.ragadmin.server.infra.storage.MinioProperties;
import com.ragadmin.server.infra.storage.support.MinioClientFactory;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class MineruImageProcessor {

    private static final Logger log = LoggerFactory.getLogger(MineruImageProcessor.class);

    private static final Pattern IMAGE_REF_PATTERN = Pattern.compile(
            "!\\[([^\\]]*)\\]\\(images/([^)]+)\\)"
    );

    private final MinioClientFactory minioClientFactory;
    private final MinioProperties minioProperties;
    private final ExecutorService ioExecutor;
    private final Semaphore concurrencyLimiter = new Semaphore(10);

    public MineruImageProcessor(
            MinioClientFactory minioClientFactory,
            MinioProperties minioProperties,
            @Qualifier("ioVirtualTaskExecutor") ExecutorService ioExecutor
    ) {
        this.minioClientFactory = minioClientFactory;
        this.minioProperties = minioProperties;
        this.ioExecutor = ioExecutor;
    }

    public ImageResolutionResult processImagesFromZip(
            byte[] zipBytes, String markdown, String bucket, Long kbId, Long documentId
    ) {
        Map<String, byte[]> imageEntries = extractImagesFromZip(zipBytes);

        if (imageEntries.isEmpty()) {
            return new ImageResolutionResult(markdown, ImageProcessingReport.empty());
        }

        List<String> warnings = new ArrayList<>();
        Map<String, String> fileNameToUrl = new HashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Map.Entry<String, byte[]> entry : imageEntries.entrySet()) {
            String fileName = entry.getKey();
            byte[] imageBytes = entry.getValue();

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    concurrencyLimiter.acquire();
                    try {
                        String objectKey = "kb/" + kbId + "/images/" + documentId + "/" + fileName;
                        minioClientFactory.createClient().putObject(
                                PutObjectArgs.builder()
                                        .bucket(bucket)
                                        .object(objectKey)
                                        .stream(new ByteArrayInputStream(imageBytes), imageBytes.length, -1)
                                        .contentType(guessContentType(fileName))
                                        .build()
                        );
                        String presignedUrl = minioClientFactory.createClient().getPresignedObjectUrl(
                                GetPresignedObjectUrlArgs.builder()
                                        .method(Method.GET)
                                        .bucket(bucket)
                                        .object(objectKey)
                                        .expiry(30 * 60)
                                        .build()
                        );
                        synchronized (fileNameToUrl) {
                            fileNameToUrl.put(fileName, presignedUrl);
                        }
                    } finally {
                        concurrencyLimiter.release();
                    }
                } catch (Exception e) {
                    String msg = "ZIP 图片上传失败: " + fileName + ", 原因: " + e.getMessage();
                    log.warn(msg, e);
                    synchronized (warnings) {
                        warnings.add(msg);
                    }
                }
            }, ioExecutor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        String rewritten = rewriteMarkdown(markdown, fileNameToUrl);
        int resolved = fileNameToUrl.size();
        int totalRefs = countImageRefs(markdown);

        ImageProcessingReport report = new ImageProcessingReport(
                totalRefs, resolved, warnings.size(), warnings
        );
        return new ImageResolutionResult(rewritten, report);
    }

    private Map<String, byte[]> extractImagesFromZip(byte[] zipBytes) {
        Map<String, byte[]> images = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(
                new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)
        ) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (!entry.isDirectory() && name.startsWith("images/") && !name.equals("images/")) {
                    String fileName = name.substring("images/".length());
                    if (StringUtils.hasText(fileName)) {
                        images.put(fileName, zis.readAllBytes());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("ZIP 图片提取失败: {}", e.getMessage(), e);
        }
        return images;
    }

    private String rewriteMarkdown(String markdown, Map<String, String> fileNameToUrl) {
        Matcher matcher = IMAGE_REF_PATTERN.matcher(markdown);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String fileName = matcher.group(2);
            String newUrl = fileNameToUrl.get(fileName);
            if (newUrl != null) {
                matcher.appendReplacement(sb, "![" + matcher.group(1) + "](" + newUrl + ")");
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private int countImageRefs(String markdown) {
        Matcher matcher = IMAGE_REF_PATTERN.matcher(markdown);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static String guessContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }
}
