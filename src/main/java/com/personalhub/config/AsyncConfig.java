package com.personalhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    /**
     * Creates a ThreadPoolTaskExecutor for GitHub synchronization tasks.
     * The executor is configured with a core pool size of 1, a maximum pool size of
     * 1, and a queue capacity of 2.
     * The thread name prefix is set to "github-sync-".
     *
     * @return a configured ThreadPoolTaskExecutor instance
     */
    @Bean("githubSyncExecutor")
    public ThreadPoolTaskExecutor githubSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(2);
        executor.setThreadNamePrefix("github-sync-");
        executor.initialize();
        return executor;
    }
}
