package com.example.medical.module.chat.repository;

import com.example.medical.module.chat.entity.AgentRunResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentRunResultRepository extends JpaRepository<AgentRunResult, Long> {

    Optional<AgentRunResult> findByRunId(Long runId);
}
