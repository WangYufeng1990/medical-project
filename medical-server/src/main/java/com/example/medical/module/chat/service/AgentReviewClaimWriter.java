package com.example.medical.module.chat.service;

import com.example.medical.module.chat.entity.AgentReviewClaim;
import com.example.medical.module.chat.repository.AgentReviewClaimRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inserts one claim in a transaction of its own (Round 57.1 review).
 * <p>
 * A concurrent first claim must not fail the caller's transaction: the unique key on the run
 * decides the winner, the loser catches the violation and reads the winner's row.
 */
@Service
@RequiredArgsConstructor
public class AgentReviewClaimWriter {

    private final AgentReviewClaimRepository claims;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(Long runId, Long staffId) {
        AgentReviewClaim claim = new AgentReviewClaim();
        claim.setRunId(runId);
        claim.setStaffId(staffId);
        claims.saveAndFlush(claim);
    }
}
