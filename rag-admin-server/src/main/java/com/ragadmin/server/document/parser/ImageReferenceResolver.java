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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ImageReferenceResolver {

    private static final Logger log = LoggerFactory.getLogger(ImageReferenceResolver.class);

    private static final Pattern IMAGE_REF_PATTERN = Pattern.compile(
            "!\\[(?<alt>[^\\]]*)\\]\\((?<url>[^)]+)\\)"
    );

    private final MinioClientFactory minioClientFactory;
    private final MinioProperties minioProperties;
    private final ImagePipelineProperties imagePipelineProperties;
    private final ExecutorService ioExecutor;
    private final Semaphore concurrencyLimiter;

    public ImageReferenceResolver(
            MinioClientFactory minioClientFactory,
            MinioProperties minioProperties,
            ImagePipelineProperties imagePipelineProperties,
            @Qualifier("ioVirtualTaskExecutor") ExecutorService ioExecutor
    ) {
        this.minioClientFactory = minioClientFactory;
        this.minioProperties = minioProperties;
        this.imagePipelineProperties = imagePipelineProperties;
        this.ioExecutor = ioExecutor;
        this.concurrencyLimiter = new Semaphore(imagePipelineProperties.getConcurrency());
    }

    public ImageResolutionResult resolveImages(
            String markdown, String bucket, Long kbId, Long documentId
    ) {
        if (!StringUtils.hasText(markdown)) {
            return new ImageResolutionResult(markdown, ImageProcessingReport.empty());
        }

        Matcher matcher = IMAGE_REF_PATTERN.matcher(markdown);
        List<ImageRef> refs = new ArrayList<>();
        while (matcher.find()) {
            refs.add(new ImageRef(matcher.group("alt"), matcher.group("url"), matcher.start(), matcher.end()));
        }

        if (refs.isEmpty()) {
            return new ImageResolutionResult(markdown, ImageProcessingReport.empty());
        }

        List<String> warnings = new ArrayList<>();
        Map<String, String> urlMappings = new HashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (ImageRef ref : refs) {
            Optional<ImageAction> action = classifyUrl(ref.url());

            if (action.isEmpty()) {
                continue;
            }

            switch (action.get()) {
                case SKIP -> { /* already in MinIO, no action */ }
                case PRESERVE_WARNING -> {
                    String msg = "无法解析图片引用（相对/本地路径），已保留原样: " + ref.url();
                    log.warn(msg);
                    warnings.add(msg);
                }
                case DOWNLOAD_AND_UPLOAD -> {
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        try {
                            concurrencyLimiter.acquire();
                            try {
                                String newUrl = downloadAndUploadToMinio(ref.url(), bucket, kbId, documentId);
                                urlMappings.put(ref.url(), newUrl);
                            } finally {
                                concurrencyLimiter.release();
                            }
                        } catch (Exception e) {
                            String msg = "图片转存失败: " + ref.url() + ", 原因: " + e.getMessage();
                            log.warn(msg, e);
                            synchronized (warnings) {
                                warnings.add(msg);
                            }
                        }
                    }, ioExecutor);
                    futures.add(future);
                }
            }
        }

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        String rewritten = markdown;
        for (Map.Entry<String, String> entry : urlMappings.entrySet()) {
            rewritten = rewritten.replace(entry.getKey(), entry.getValue());
        }

        int resolved = urlMappings.size();
        ImageProcessingReport report = new ImageProcessingReport(
                refs.size(), resolved, warnings.size(), warnings
        );
        return new ImageResolutionResult(rewritten, report);
    }

    private Optional<ImageAction> classifyUrl(String url) {
        String lower = url.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            String host = URI.create(url).getHost();
            String minioHost = minioProperties.getBaseUrl().replaceFirst("^https?://", "").split(":")[0];
            if (host != null && host.equals(minioHost)) {
                return Optional.of(ImageAction.SKIP);
            }
            return Optional.of(ImageAction.DOWNLOAD_AND_UPLOAD);
        }
        if (lower.startsWith("/")) {
            return Optional.of(ImageAction.PRESERVE_WARNING);
        }
        if (!lower.contains(":")) {
            return Optional.of(ImageAction.PRESERVE_WARNING);
        }
        return Optional.empty();
    }

    private String downloadAndUploadToMinio(String imageUrl, String bucket, Long kbId, Long documentId) {
        try {
            byte[] imageBytes = downloadImage(imageUrl);
            String extension = extractExtension(imageUrl);
            String hash = sha256Hex(imageUrl);
            String objectKey = "kb/" + kbId + "/images/" + documentId + "/" + hash + "." + extension;

            minioClientFactory.createClient().putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(new ByteArrayInputStream(imageBytes), imageBytes.length, -1)
                            .contentType(guessContentType(extension))
                            .build()
            );

            return generatePresignedUrl(bucket, objectKey);
        } catch (Exception e) {
            throw new RuntimeException("图片下载转存失败: " + imageUrl, e);
        }
    }

    private byte[] downloadImage(String imageUrl) {
        try (var stream = URI.create(imageUrl).toURL().openStream()) {
            byte[] bytes = stream.readAllBytes();
            if (bytes.length > imagePipelineProperties.getMaxImageSize()) {
                throw new RuntimeException("图片超过 10MB 限制: " + imageUrl);
            }
            return bytes;
        } catch (Exception e) {
            throw new RuntimeException("图片下载失败: " + imageUrl, e);
        }
    }

    private String generatePresignedUrl(String bucket, String objectKey) throws Exception {
        return minioClientFactory.createClient().getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucket)
                        .object(objectKey)
                        .expiry(imagePipelineProperties.getPresignedUrlExpirySeconds())
                        .build()
        );
    }

    private static String extractExtension(String url) {
        String path = URI.create(url).getPath();
        if (path != null) {
            int dotIdx = path.lastIndexOf('.');
            if (dotIdx > 0) {
                String ext = path.substring(dotIdx + 1).toLowerCase();
                if (ext.matches("^(png|jpe?g|gif|webp|svg|bmp|tiff?)$")) {
                    return ext;
                }
            }
        }
        return "png";
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String guessContentType(String ext) {
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            default -> "application/octet-stream";
        };
    }

    private enum ImageAction {
        SKIP,
        PRESERVE_WARNING,
        DOWNLOAD_AND_UPLOAD
    }

    private record ImageRef(String alt, String url, int start, int end) {
    }
}
