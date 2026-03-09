package com.ragadmin.server.infra.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "rag.storage.minio")
public class MinioProperties {

    private String endpoint;
    private Integer port = 9000;
    private boolean useSsl;
    private String accessKey;
    private String secretKey;
    private String bucketName;
    private String publicUrl;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public boolean isUseSsl() {
        return useSsl;
    }

    public void setUseSsl(boolean useSsl) {
        this.useSsl = useSsl;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(endpoint)
                && StringUtils.hasText(accessKey)
                && StringUtils.hasText(secretKey)
                && StringUtils.hasText(bucketName);
    }

    public String getBaseUrl() {
        if (!StringUtils.hasText(endpoint)) {
            return "";
        }
        String scheme = useSsl ? "https" : "http";
        return scheme + "://" + endpoint + ":" + port;
    }
}
