package com.ragadmin.server.model.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateModelProviderRequest {

    @NotBlank(message = "providerCode 不能为空")
    private String providerCode;

    @NotBlank(message = "providerName 不能为空")
    private String providerName;

    private String baseUrl;
    private String apiKeySecretRef;
    @NotBlank(message = "status 不能为空")
    private String status;

    public String getProviderCode() {
        return providerCode;
    }

    public void setProviderCode(String providerCode) {
        this.providerCode = providerCode;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKeySecretRef() {
        return apiKeySecretRef;
    }

    public void setApiKeySecretRef(String apiKeySecretRef) {
        this.apiKeySecretRef = apiKeySecretRef;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
