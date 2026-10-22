package com.example.medical.module.chat.service;

import com.example.medical.module.chat.entity.AgentRunState;
import com.example.medical.module.chat.repository.AgentRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * The transaction boundary for queue state changes (Round 55.2).
 * <p>
 * Each of these is its own short transaction on purpose: the claim must be committed
 * before the dispatcher calls the agent service, so a second dispatcher sees a job that is
 * already taken — and a slow model must never hold a database transaction open.
 */
@Service
@RequiredArgsConstructor
public class AgentRunStateStore {

    private final AgentRunRepository runs;

    @Transactional
    public int claim(Long id, String owner, LocalDateTime leaseUntil, LocalDateTime now) {
        return runs.claim(id, owner, leaseUntil, now,
                AgentRunState.RUNNING.name(), AgentRunState.PENDING.name());
    }

    @Transactional
    public void markRunning(Long id, Long generation) {
        runs.markState(id, AgentRunState.RUNNING.name(), generation, LocalDateTime.now());
    }

    @Transactional
    public void retryLater(Long id, Long generation, LocalDateTime nextAttemptAt, String error) {
        runs.retryLater(id, nextAttemptAt, error, generation, LocalDateTime.now(),
                AgentRunState.PENDING.name());
    }

    @Transactional
    public void fail(Long id, Long generation, String error) {
        runs.transition(id, AgentRunState.FAILED.name(), error, generation, LocalDateTime.now());
    }

    /** A worker's result: RUNNING → WAITING_REVIEW, only for the generation it was given. */
    @Transactional
    public boolean complete(Long id, Long generation) {
        return runs.complete(id, generation, AgentRunState.WAITING_REVIEW.name(),
                LocalDateTime.now(), AgentRunState.RUNNING.name()) == 1;
    }

    @Transactional
    public int reclaimExpired(LocalDateTime now) {
        return runs.reclaimExpired(now, AgentRunState.RUNNING.name(), AgentRunState.PENDING.name());
    }

    @Transactional
    public int failExhaustedPending(LocalDateTime now, String reason) {
        return runs.failExhaustedPending(now, AgentRunState.PENDING.name(),
                AgentRunState.FAILED.name(), reason);
    }

    @Transactional
    public int failExhaustedRunning(LocalDateTime now, String reason) {
        return runs.failExhaustedRunning(now, AgentRunState.RUNNING.name(),
                AgentRunState.FAILED.name(), reason);
    }

    @Transactional
    public int expireDeadlines(LocalDateTime now, String reason) {
        return runs.expireDeadlines(now, AgentRunState.PENDING.name(), AgentRunState.RUNNING.name(),
                AgentRunState.FAILED.name(), reason);
    }
}
