package com.example.medical.module.chat.agent;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.chat.entity.AgentCapability;
import com.example.medical.module.chat.entity.AgentRun;
import com.example.medical.module.chat.entity.AgentRunState;
import com.example.medical.module.chat.repository.AgentCapabilityRepository;
import com.example.medical.module.chat.repository.AgentRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Short-lived capabilities for one execution attempt (Round 55.3).
 * <p>
 * The raw token exists only in the response to the dispatch call and in the worker's
 * memory; the database stores a digest. A capability is bound to the run and to the
 * generation that issued it, so a retry fences the previous worker rather than sharing
 * access with it, and verification checks the run's <em>current</em> state — a completed or
 * reclaimed run cannot be driven with an old token.
 */
@Service
@RequiredArgsConstructor
public class AgentCapabilityService {

    private static final int TOKEN_BYTES = 32;

    private final AgentCapabilityRepository capabilities;
    private final AgentRunRepository runs;
    private final AgentDispatchProperties properties;
    private final SecureRandom random = new SecureRandom();

    /** Issues a capability for this attempt and returns the raw token (never stored). */
    @Transactional
    public String issue(AgentRun run) {
        byte[] token = new byte[TOKEN_BYTES];
        random.nextBytes(token);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(token);

        AgentCapability capability = new AgentCapability();
        capability.setRunId(run.getId());
        capability.setDigest(digest(raw));
        capability.setGeneration(run.getExecutionGeneration());
        capability.setExpiresAt(LocalDateTime.now().plus(properties.getCapabilityTtl()));
        capabilities.save(capability);
        return raw;
    }

    /**
     * The run this capability may act on, or a refusal. Every check is here on purpose: the
     * caller does not get to decide which of them apply.
     */
    public AgentRun verify(Long runId, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "an execution capability is required");
        }
        AgentCapability capability = capabilities.findByDigest(digest(rawToken))
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "unknown capability"));
        if (!capability.getRunId().equals(runId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "capability does not belong to this run");
        }
        if (!capability.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "capability expired");
        }
        AgentRun run = runs.findById(runId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "run not found"));
        if (!run.getExecutionGeneration().equals(capability.getGeneration())) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "capability was issued for a previous execution generation");
        }
        if (!AgentRunState.RUNNING.name().equals(run.getState())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "run is not accepting work");
        }
        // The capability can outlive the lease it was issued under (5 minutes vs 60
        // seconds), so a worker whose lease ran out must be refused here even before the
        // reconcile sweep notices.
        LocalDateTime now = LocalDateTime.now();
        if (run.getLeaseExpiresAt() == null || !run.getLeaseExpiresAt().isAfter(now)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "the run's lease has expired");
        }
        if (run.getDeadlineAt() == null || !run.getDeadlineAt().isAfter(now)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "the run is past its deadline");
        }
        return run;
    }

    /** Ends the capability set of a run once it stops accepting work. */
    @Transactional
    public void revoke(Long runId) {
        capabilities.deleteByRunId(runId);
    }

    static String digest(String raw) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }
}
