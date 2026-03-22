package com.ragadmin.server.common.config;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncExecutionConfigurationTest {

    @Test
    void shouldCreateNamedVirtualThreadsForIoExecutor() throws Exception {
        AsyncExecutionConfiguration configuration = new AsyncExecutionConfiguration(new AsyncExecutionProperties());

        try (ExecutorService executorService = configuration.ioVirtualTaskExecutor()) {
            String threadInfo = executorService.submit(
                    () -> Thread.currentThread().getName() + "|" + Thread.currentThread().isVirtual()
            ).get(5, TimeUnit.SECONDS);

            String[] parts = threadInfo.split("\\|", 2);
            assertTrue(Boolean.parseBoolean(parts[1]));
            assertTrue(parts[0].startsWith("rag-io-"));
        }
    }
}
