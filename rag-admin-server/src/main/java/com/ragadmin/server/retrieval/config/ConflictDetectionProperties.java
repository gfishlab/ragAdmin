package com.ragadmin.server.retrieval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "rag.retrieval.conflict-detection")
public class ConflictDetectionProperties {

    private boolean enabled = false;
    private String method = "PROMPT"; // PROMPT | LLM_NLI

    @NestedConfigurationProperty
    private LlmNliProperties llmNli = new LlmNliProperties();

    @NestedConfigurationProperty
    private ResolutionProperties resolution = new ResolutionProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public LlmNliProperties getLlmNli() {
        return llmNli;
    }

    public void setLlmNli(LlmNliProperties llmNli) {
        this.llmNli = llmNli;
    }

    public ResolutionProperties getResolution() {
        return resolution;
    }

    public void setResolution(ResolutionProperties resolution) {
        this.resolution = resolution;
    }

    public boolean isLlmNliMode() {
        return enabled && "LLM_NLI".equalsIgnoreCase(method);
    }

    public static class LlmNliProperties {
        private String modelProvider;
        private double temperature = 0.0;
        private double topP = 0.1;
        private int maxPairs = 20;
        private double confidenceThreshold = 0.7;

        public String getModelProvider() {
            return modelProvider;
        }

        public void setModelProvider(String modelProvider) {
            this.modelProvider = modelProvider;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public double getTopP() {
            return topP;
        }

        public void setTopP(double topP) {
            this.topP = topP;
        }

        public int getMaxPairs() {
            return maxPairs;
        }

        public void setMaxPairs(int maxPairs) {
            this.maxPairs = maxPairs;
        }

        public double getConfidenceThreshold() {
            return confidenceThreshold;
        }

        public void setConfidenceThreshold(double confidenceThreshold) {
            this.confidenceThreshold = confidenceThreshold;
        }
    }

    public static class ResolutionProperties {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
