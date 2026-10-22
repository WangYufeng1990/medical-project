package com.example.medical.module.chat.service;

import com.example.medical.module.chat.agent.AgentDispatchProperties;
import com.example.medical.module.chat.agent.AgentRunClient;
import com.example.medical.module.chat.dto.agent.AgentRunStartRequest;
import com.example.medical.module.chat.entity.AgentRun;
import com.example.medical.module.chat.entity.AgentRunState;
import com.example.medical.module.chat.entity.Message;
import com.example.medical.module.chat.repository.AgentRunRepository;
import com.example.medical.module.chat.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The durable dispatcher (Round 55.2).
 * <p>
 * Two properties matter more than throughput. A message that is stored has a job, because
 * intake happens in the message's own transaction; and a job is attempted by one dispatcher
 * at a time, because the claim is a guarded UPDATE rather than a read-then-write. Everything
 * else — backoff, deadline, the reclaim sweep — exists so a failure ends in a state instead
 * of in a lost message.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunService {

    private static final int MAX_ERROR_LENGTH = 500;

    private final AgentRunRepository runs;
    private final AgentRunStateStore states;
    private final MessageRepository messages;
    private final ChatConversationService conversations;
    private final AgentRunClient client;
    private final AgentDispatchProperties properties;

    public boolean enabled() {
        return properties.isEnabled();
    }

    /**
     * Records the job for a stored patient message. Called inside the send transaction, and
     * idempotent on the message id so a retried intake cannot queue the same message twice.
     */
    @Transactional
    public void recordForMessage(Message message, Long patientId) {
        if (!properties.isEnabled() || message.getId() == null || patientId == null) return;
        if (runs.existsByMessageId(message.getId())) return;

        LocalDateTime now = LocalDateTime.now();
        long revision = message.getSenderType() != null && message.getReceiverType() != null
                ? conversations.revisionOf(
                        new Party(message.getSenderType(), message.getSenderId()),
                        new Party(message.getReceiverType(), message.getReceiverId()))
                : 0L;

        AgentRun run = new AgentRun();
        run.setMessageId(message.getId());
        run.setPatientId(patientId);
        run.setConversationRevision(revision);
        run.setState(AgentRunState.PENDING.name());
        run.setAttempt(0);
        run.setMaxAttempts(properties.getMaxAttempts());
        run.setNextAttemptAt(now);
        run.setDeadlineAt(now.plus(properties.getDeadline()));
        run.setExecutionGeneration(0L);
        runs.save(run);
    }

    /**
     * Claims and dispatches the jobs that are due.
     *
     * @return how many jobs this caller claimed and attempted
     */
    public int dispatchDue() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> due = runs.findDueIds(AgentRunState.PENDING.name(), now,
                PageRequest.of(0, Math.max(1, properties.getBatchSize())));
        int dispatched = 0;
        for (Long id : due) {
            int claimed = states.claim(id, properties.ownerId(), now.plus(properties.getLease()), now);
            if (claimed == 0) continue; // another dispatcher owns it now
            deliver(id);
            dispatched++;
        }
        return dispatched;
    }

    /**
     * Returns jobs whose lease ran out to the queue and ends jobs that outlived their
     * deadline. Called on the same schedule as the dispatch pass; a dispatcher restart is
     * not needed to recover a job whose worker disappeared.
     */
    public int reconcile() {
        LocalDateTime now = LocalDateTime.now();
        int reclaimed = states.reclaimExpired(now);
        int expired = states.expireDeadlines(now, "agent run deadline exceeded");
        if (reclaimed > 0 || expired > 0) {
            log.info("Agent runs reconciled: {} reclaimed, {} failed past deadline", reclaimed, expired);
        }
        return reclaimed + expired;
    }

    private void deliver(Long id) {
        LocalDateTime now = LocalDateTime.now();
        AgentRun run = runs.findById(id).orElse(null);
        if (run == null) return;
        Message message = messages.findById(run.getMessageId()).orElse(null);
        if (message == null) {
            states.fail(id, "message no longer exists");
            return;
        }
        try {
            client.start(AgentRunStartRequest.of(run, message));
            states.markRunning(id);
        } catch (RuntimeException failure) {
            String error = truncate(failure.getMessage() == null
                    ? failure.getClass().getSimpleName() : failure.getMessage());
            int attempt = run.getAttempt() == null ? 0 : run.getAttempt();
            int maxAttempts = run.getMaxAttempts() == null ? properties.getMaxAttempts() : run.getMaxAttempts();
            if (attempt >= maxAttempts) {
                states.fail(id, error);
                log.warn("Agent run {} failed after {} attempts: {}", id, attempt, error);
            } else {
                states.retryLater(id, LocalDateTime.now().plus(backoff(attempt)), error);
                log.debug("Agent run {} attempt {} failed, retrying later: {}", id, attempt, error);
            }
        }
    }

    /** Exponential from the configured base, capped so a long outage still retries hourly. */
    private Duration backoff(int attempt) {
        Duration base = properties.getBaseBackoff();
        int exponent = Math.max(0, attempt - 1);
        long millis = base.toMillis() * (1L << Math.min(exponent, 20));
        return Duration.ofMillis(Math.min(millis, properties.getMaxBackoff().toMillis()));
    }

    private String truncate(String error) {
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
