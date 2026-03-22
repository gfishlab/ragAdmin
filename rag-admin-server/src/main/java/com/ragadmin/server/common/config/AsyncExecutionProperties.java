package com.ragadmin.server.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.async")
public class AsyncExecutionProperties {

    private final Application application = new Application();
    private final WebMvcStreaming webMvcStreaming = new WebMvcStreaming();
    private final Scheduler scheduler = new Scheduler();

    public Application getApplication() {
        return application;
    }

    public WebMvcStreaming getWebMvcStreaming() {
        return webMvcStreaming;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public static class Application {

        private int coreSize = 8;
        private int maxSize = 16;
        private int queueCapacity = 200;
        private int keepAliveSeconds = 60;

        public int getCoreSize() {
            return coreSize;
        }

        public void setCoreSize(int coreSize) {
            this.coreSize = coreSize;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public int getKeepAliveSeconds() {
            return keepAliveSeconds;
        }

        public void setKeepAliveSeconds(int keepAliveSeconds) {
            this.keepAliveSeconds = keepAliveSeconds;
        }
    }

    public static class WebMvcStreaming {

        private int coreSize = 4;
        private int maxSize = 16;
        private int queueCapacity = 200;
        private int keepAliveSeconds = 60;
        private long requestTimeoutMs = 0L;

        public int getCoreSize() {
            return coreSize;
        }

        public void setCoreSize(int coreSize) {
            this.coreSize = coreSize;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public int getKeepAliveSeconds() {
            return keepAliveSeconds;
        }

        public void setKeepAliveSeconds(int keepAliveSeconds) {
            this.keepAliveSeconds = keepAliveSeconds;
        }

        public long getRequestTimeoutMs() {
            return requestTimeoutMs;
        }

        public void setRequestTimeoutMs(long requestTimeoutMs) {
            this.requestTimeoutMs = requestTimeoutMs;
        }
    }

    public static class Scheduler {

        private int poolSize = 1;

        public int getPoolSize() {
            return poolSize;
        }

        public void setPoolSize(int poolSize) {
            this.poolSize = poolSize;
        }
    }
}
