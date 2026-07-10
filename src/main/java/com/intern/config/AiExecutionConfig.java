package com.intern.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AiExecutionConfig {
    @Bean(name = "aiModelExecutor")
    public Executor aiModelExecutor(
            @Value("${nexusmind.ai.executor.core-size:4}") int coreSize,
            @Value("${nexusmind.ai.executor.max-size:8}") int maxSize,
            @Value("${nexusmind.ai.executor.queue-capacity:40}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ai-model-");
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
