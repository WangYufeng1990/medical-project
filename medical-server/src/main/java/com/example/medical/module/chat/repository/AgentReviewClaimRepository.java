package com.example.medical.module.chat.repository;

import com.example.medical.module.chat.entity.AgentReviewClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentReviewClaimRepository extends JpaRepository<AgentReviewClaim, Long> {

    Optional<AgentReviewClaim> findByRunId(Long runId);
}
