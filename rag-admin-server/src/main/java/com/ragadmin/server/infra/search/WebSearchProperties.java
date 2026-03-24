package com.ragadmin.server.infra.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.search")
public class WebSearchProperties {

    private boolean enabled = true;
    private int defaultTopK = 5;
    private int contextMaxChars = 2000;
    private int logQueryMaxChars = 120;
    private int resultTitleMaxChars = 120;
    private int resultSnippetMaxChars = 320;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getDefaultTopK() {
        return defaultTopK;
    }

    public void setDefaultTopK(int defaultTopK) {
        this.defaultTopK = defaultTopK;
    }

    public int getContextMaxChars() {
        return contextMaxChars;
    }

    public void setContextMaxChars(int contextMaxChars) {
        this.contextMaxChars = contextMaxChars;
    }

    public int getLogQueryMaxChars() {
        return logQueryMaxChars;
    }

    public void setLogQueryMaxChars(int logQueryMaxChars) {
        this.logQueryMaxChars = logQueryMaxChars;
    }

    public int getResultTitleMaxChars() {
        return resultTitleMaxChars;
    }

    public void setResultTitleMaxChars(int resultTitleMaxChars) {
        this.resultTitleMaxChars = resultTitleMaxChars;
    }

    public int getResultSnippetMaxChars() {
        return resultSnippetMaxChars;
    }

    public void setResultSnippetMaxChars(int resultSnippetMaxChars) {
        this.resultSnippetMaxChars = resultSnippetMaxChars;
    }
}
