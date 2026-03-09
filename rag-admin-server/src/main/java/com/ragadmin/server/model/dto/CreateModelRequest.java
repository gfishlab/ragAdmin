package com.ragadmin.server.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public class CreateModelRequest {

    @NotNull(message = "providerId 不能为空")
    private Long providerId;

    @NotBlank(message = "modelCode 不能为空")
    private String modelCode;

    @NotBlank(message = "modelName 不能为空")
    private String modelName;

    @NotEmpty(message = "capabilityTypes 不能为空")
    private List<String> capabilityTypes;

    @NotBlank(message = "modelType 不能为空")
    private String modelType;

    private Integer maxTokens;
    private BigDecimal temperatureDefault;

    @NotBlank(message = "status 不能为空")
    private String status;

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public String getModelCode() {
        return modelCode;
    }

    public void setModelCode(String modelCode) {
        this.modelCode = modelCode;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public List<String> getCapabilityTypes() {
        return capabilityTypes;
    }

    public void setCapabilityTypes(List<String> capabilityTypes) {
        this.capabilityTypes = capabilityTypes;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public BigDecimal getTemperatureDefault() {
        return temperatureDefault;
    }

    public void setTemperatureDefault(BigDecimal temperatureDefault) {
        this.temperatureDefault = temperatureDefault;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
