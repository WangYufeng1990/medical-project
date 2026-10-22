package com.example.medical.module.chat.agent;

import com.example.medical.module.chat.service.AgentRunService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Drives the dispatcher on its own thread (Round 55.2); a failure here never throws out. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRunScheduler {

    private final AgentRunService service;
    private final AgentDispatchProperties properties;

    @Scheduled(fixedDelayString = "${app.agent.poll-interval-ms:5000}",
            scheduler = "agentDispatchScheduler")
    public void poll() {
        if (!properties.isEnabled() || !properties.isSchedulingEnabled()) return;
        try {
            service.dispatchDue();
            service.reconcile();
        } catch (RuntimeException e) {
            log.warn("Agent dispatch pass failed; the rows stay durable", e);
        }
    }
}
