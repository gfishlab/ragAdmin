package com.ragadmin.server.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class WebMvcAsyncConfigurationTest {

    @Test
    void shouldConfigureDedicatedExecutorAndTimeoutForWebMvcStreaming() {
        AsyncExecutionProperties properties = new AsyncExecutionProperties();
        properties.getWebMvcStreaming().setRequestTimeoutMs(180000L);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("test-mvc-stream-");
        executor.initialize();

        try {
            WebMvcAsyncConfiguration configuration = new WebMvcAsyncConfiguration(executor, properties);
            AsyncSupportConfigurer configurer = new AsyncSupportConfigurer();

            configuration.configureAsyncSupport(configurer);

            assertSame(executor, ReflectionTestUtils.invokeMethod(configurer, "getTaskExecutor"));
            assertEquals(Long.valueOf(180000L), ReflectionTestUtils.invokeMethod(configurer, "getTimeout"));
        } finally {
            executor.shutdown();
        }
    }
}
