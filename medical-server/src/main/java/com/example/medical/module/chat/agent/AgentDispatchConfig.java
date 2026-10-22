package com.example.medical.module.chat.agent;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * A scheduler of its own for the agent work (Round 55.2): a slow model must not delay the
 * audit or quality schedulers, and their delay must not delay dispatch.
 */
@Configuration
public class AgentDispatchConfig {

    @Bean(name = "agentDispatchScheduler")
    public ThreadPoolTaskScheduler agentDispatchScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("agent-dispatch-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(5);
        return scheduler;
    }
}
