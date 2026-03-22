package com.ragadmin.server.web;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

final class MockMvcAsyncTestSupport {

    private static final ThreadPoolTaskExecutor STREAMING_TASK_EXECUTOR = createStreamingTaskExecutor();

    private MockMvcAsyncTestSupport() {
    }

    static MockMvc withStreamingExecutor(MockMvc mockMvc) {
        WebApplicationContext applicationContext = (WebApplicationContext) mockMvc.getDispatcherServlet().getWebApplicationContext();
        RequestMappingHandlerAdapter handlerAdapter = applicationContext.getBean(RequestMappingHandlerAdapter.class);
        handlerAdapter.setTaskExecutor(STREAMING_TASK_EXECUTOR);
        handlerAdapter.setAsyncRequestTimeout(0L);
        try {
            // standalone MockMvc 会在 build 阶段完成 handler 初始化，这里补一次刷新让新执行器真正生效。
            handlerAdapter.afterPropertiesSet();
        } catch (Exception ex) {
            throw new IllegalStateException("配置 MockMvc 异步执行器失败", ex);
        }
        return mockMvc;
    }

    private static ThreadPoolTaskExecutor createStreamingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("rag-test-mvc-stream-");
        executor.initialize();
        return executor;
    }
}
