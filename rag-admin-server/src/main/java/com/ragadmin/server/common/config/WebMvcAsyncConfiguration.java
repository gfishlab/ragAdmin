package com.ragadmin.server.common.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcAsyncConfiguration implements WebMvcConfigurer {

    private final AsyncTaskExecutor webMvcStreamingTaskExecutor;
    private final AsyncExecutionProperties asyncExecutionProperties;

    @Autowired
    public WebMvcAsyncConfiguration(
            @Qualifier(AsyncExecutionConfiguration.WEB_MVC_STREAMING_TASK_EXECUTOR)
            AsyncTaskExecutor webMvcStreamingTaskExecutor,
            AsyncExecutionProperties asyncExecutionProperties
    ) {
        this.webMvcStreamingTaskExecutor = webMvcStreamingTaskExecutor;
        this.asyncExecutionProperties = asyncExecutionProperties;
    }

    /**
     * Spring MVC 下的 Flux/SSE 仍会走 Servlet 异步链路，需要显式指定执行器，避免回退到默认值。
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(webMvcStreamingTaskExecutor);
        configurer.setDefaultTimeout(Math.max(0L, asyncExecutionProperties.getWebMvcStreaming().getRequestTimeoutMs()));
    }
}
