package com.ragadmin.server.document.parser;

import com.ragadmin.server.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class DefaultMineruParseService implements MineruParseService {

    private static final Logger log = LoggerFactory.getLogger(DefaultMineruParseService.class);

    private final MineruProperties mineruProperties;
    private final MineruSourceResolver mineruSourceResolver;
    private final RestClient restClient;
    private final RestClient downloadClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DefaultMineruParseService(MineruProperties mineruProperties, MineruSourceResolver mineruSourceResolver) {
        this.mineruProperties = mineruProperties;
        this.mineruSourceResolver = mineruSourceResolver;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(Math.max(5, mineruProperties.getTimeoutSeconds())));
        requestFactory.setReadTimeout(Duration.ofSeconds(Math.max(5, mineruProperties.getTimeoutSeconds())));
        this.restClient = RestClient.builder()
                .baseUrl(mineruProperties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + nullSafe(mineruProperties.getApiToken()))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(requestFactory)
                .build();
        this.downloadClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public List<Document> parse(DocumentParseRequest request) throws Exception {
        if (!mineruProperties.isConfigured()) {
            throw new BusinessException("MINERU_NOT_CONFIGURED", "MinerU API 未完成配置", HttpStatus.SERVICE_UNAVAILABLE);
        }
        String sourceUrl = mineruSourceResolver.resolve(request);
        return parseByUrl(sourceUrl, request.document().getDocName());
    }

    @Override
    public List<Document> parseByUrl(String presignedUrl, String fileName) throws Exception {
        if (!mineruProperties.isConfigured()) {
            throw new BusinessException("MINERU_NOT_CONFIGURED", "MinerU API 未完成配置", HttpStatus.SERVICE_UNAVAILABLE);
        }
        MineruTaskModels.CreateTaskResponse createTaskResponse = submitTask(presignedUrl, fileName);
        MineruTaskModels.TaskResultResponse taskResultResponse = pollUntilFinished(createTaskResponse.data().taskId());
        if (!"done".equalsIgnoreCase(taskResultResponse.data().state())) {
            log.warn("MinerU 解析失败，taskId={}, fileName={}, errMsg={}",
                    createTaskResponse.data().taskId(), fileName, nullSafe(taskResultResponse.data().errMsg()));
            throw new BusinessException(
                    "MINERU_TASK_FAILED",
                    "MinerU 解析失败: " + nullSafe(taskResultResponse.data().errMsg()),
                    HttpStatus.BAD_GATEWAY
            );
        }
        MineruTaskModels.MarkdownResult markdownResult = resolveMarkdown(taskResultResponse.data());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("mineruTaskId", createTaskResponse.data().taskId());
        metadata.put("mineruState", taskResultResponse.data().state());
        metadata.put("mineruResultSource", markdownResult.source());
        return List.of(new Document(markdownResult.markdown(), metadata));
    }

    @Override
    public OcrCapability describeCapability() {
        if (!mineruProperties.isEnabled()) {
            return new OcrCapability(false, false, "MinerU API 已禁用", mineruProperties.getLanguage(), mineruProperties.getMaxPdfPages());
        }
        if (!mineruProperties.isConfigured()) {
            return new OcrCapability(true, false, "MinerU API 未完成配置", mineruProperties.getLanguage(), mineruProperties.getMaxPdfPages());
        }
        return new OcrCapability(true, true, "MinerU API 可用", mineruProperties.getLanguage(), mineruProperties.getMaxPdfPages());
    }

    protected MineruTaskModels.CreateTaskResponse submitTask(String sourceUrl, String fileName) {
        MineruTaskModels.CreateTaskRequest payload = new MineruTaskModels.CreateTaskRequest(
                sourceUrl,
                mineruProperties.getModelVersion(),
                fileName
        );
        MineruTaskModels.CreateTaskResponse response;
        try {
            String requestBody = objectMapper.writeValueAsString(payload);
            response = restClient.post()
                    .uri("/api/v4/extract/task")
                    .body(requestBody)
                    .retrieve()
                    .body(MineruTaskModels.CreateTaskResponse.class);
        } catch (RestClientResponseException ex) {
            log.warn("MinerU 任务创建失败，fileName={}, statusCode={}, body={}",
                    fileName, ex.getStatusCode(), truncate(ex.getResponseBodyAsString()));
            throw new BusinessException("MINERU_TASK_CREATE_FAILED", "MinerU 任务创建失败", HttpStatus.BAD_GATEWAY);
        } catch (Exception ex) {
            log.warn("MinerU 任务创建异常，fileName={}, reason={}", fileName, ex.getMessage());
            throw new BusinessException("MINERU_TASK_CREATE_FAILED", "MinerU 任务创建失败", HttpStatus.BAD_GATEWAY);
        }
        if (response == null || response.data() == null || !StringUtils.hasText(response.data().taskId())) {
            throw new BusinessException("MINERU_TASK_CREATE_FAILED", "MinerU 任务创建失败", HttpStatus.BAD_GATEWAY);
        }
        log.info("MinerU 任务创建成功，taskId={}, fileName={}", response.data().taskId(), fileName);
        return response;
    }

    protected MineruTaskModels.TaskResultResponse pollUntilFinished(String taskId) throws InterruptedException {
        for (int i = 0; i < mineruProperties.getMaxPollAttempts(); i++) {
            MineruTaskModels.TaskResultResponse response;
            try {
                response = restClient.get()
                        .uri("/api/v4/extract/task/{taskId}", taskId)
                        .retrieve()
                        .body(MineruTaskModels.TaskResultResponse.class);
            } catch (RestClientResponseException ex) {
                log.warn("MinerU 任务轮询失败，taskId={}, statusCode={}, body={}",
                        taskId, ex.getStatusCode(), truncate(ex.getResponseBodyAsString()));
                throw new BusinessException("MINERU_TASK_POLL_FAILED", "MinerU 任务轮询失败", HttpStatus.BAD_GATEWAY);
            } catch (Exception ex) {
                log.warn("MinerU 任务轮询异常，taskId={}, reason={}", taskId, ex.getMessage());
                throw new BusinessException("MINERU_TASK_POLL_FAILED", "MinerU 任务轮询失败", HttpStatus.BAD_GATEWAY);
            }
            if (response != null && response.data() != null) {
                String state = nullSafe(response.data().state()).toLowerCase(Locale.ROOT);
                if ("done".equals(state) || "failed".equals(state)) {
                    log.info("MinerU 任务结束，taskId={}, state={}", taskId, state);
                    return response;
                }
            }
            Thread.sleep(mineruProperties.getPollIntervalMillis());
        }
        throw new BusinessException("MINERU_TASK_TIMEOUT", "MinerU 解析超时", HttpStatus.GATEWAY_TIMEOUT);
    }

    protected MineruTaskModels.MarkdownResult resolveMarkdown(MineruTaskModels.TaskResultData taskResultData) throws Exception {
        if (StringUtils.hasText(taskResultData.fullMdUrl())) {
            return new MineruTaskModels.MarkdownResult(downloadPlainText(taskResultData.fullMdUrl()), "FULL_MD_URL");
        }
        if (StringUtils.hasText(taskResultData.mdUrl())) {
            return new MineruTaskModels.MarkdownResult(downloadPlainText(taskResultData.mdUrl()), "MD_URL");
        }
        if (StringUtils.hasText(taskResultData.fullZipUrl())) {
            return new MineruTaskModels.MarkdownResult(downloadMarkdownFromZip(taskResultData.fullZipUrl()), "FULL_ZIP_URL");
        }
        throw new BusinessException("MINERU_RESULT_MISSING", "MinerU 未返回可用的 Markdown 结果地址", HttpStatus.BAD_GATEWAY);
    }

    protected String downloadPlainText(String fileUrl) {
        String body;
        try {
            body = downloadClient
                    .get()
                    .uri(URI.create(fileUrl))
                    .retrieve()
                    .body(String.class);
        } catch (Exception ex) {
            log.warn("MinerU Markdown 下载失败，url={}, reason={}", fileUrl, ex.getMessage());
            throw new BusinessException("MINERU_RESULT_DOWNLOAD_FAILED", "MinerU Markdown 下载失败", HttpStatus.BAD_GATEWAY);
        }
        if (!StringUtils.hasText(body)) {
            throw new BusinessException("MINERU_RESULT_EMPTY", "MinerU 返回的 Markdown 内容为空", HttpStatus.BAD_GATEWAY);
        }
        return body;
    }

    protected String downloadMarkdownFromZip(String fullZipUrl) throws Exception {
        byte[] zipBytes;
        try {
            zipBytes = downloadClient
                    .get()
                    .uri(URI.create(fullZipUrl))
                    .retrieve()
                    .body(byte[].class);
        } catch (Exception ex) {
            log.warn("MinerU 结果压缩包下载失败，url={}, reason={}", fullZipUrl, ex.getMessage());
            throw new BusinessException("MINERU_RESULT_DOWNLOAD_FAILED", "MinerU 结果压缩包下载失败", HttpStatus.BAD_GATEWAY);
        }
        if (zipBytes == null || zipBytes.length == 0) {
            throw new BusinessException("MINERU_RESULT_EMPTY", "MinerU 结果压缩包为空", HttpStatus.BAD_GATEWAY);
        }
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().endsWith("full.md") || entry.getName().endsWith(".md")) {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    zipInputStream.transferTo(outputStream);
                    String markdown = outputStream.toString(StandardCharsets.UTF_8);
                    if (StringUtils.hasText(markdown)) {
                        return markdown;
                    }
                }
            }
        }
        throw new BusinessException("MINERU_RESULT_MISSING", "MinerU 结果压缩包中未找到有效 Markdown 文件", HttpStatus.BAD_GATEWAY);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

}
