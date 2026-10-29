package com.example.medical.module.chat.repository;

import com.example.medical.module.chat.entity.AgentReviewDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentReviewDecisionRepository extends JpaRepository<AgentReviewDecision, Long> {

    Optional<AgentReviewDecision> findByRunId(Long runId);
}
