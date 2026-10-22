package com.example.medical.module.chat.repository;

import com.example.medical.module.chat.entity.AgentCapability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AgentCapabilityRepository extends JpaRepository<AgentCapability, Long> {

    Optional<AgentCapability> findByDigest(String digest);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM AgentCapability c WHERE c.runId = :runId")
    int deleteByRunId(@Param("runId") Long runId);
}
