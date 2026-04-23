package com.ragadmin.server.document.parser;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.document.conversion")
public class ConversionProperties {

    private boolean enabled = true;
    private String libreOfficePath = "libreoffice";
    private int timeoutSeconds = 60;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getLibreOfficePath() {
        return libreOfficePath;
    }

    public void setLibreOfficePath(String libreOfficePath) {
        this.libreOfficePath = libreOfficePath;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
