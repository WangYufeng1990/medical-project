package com.example.medical.module.chat.repository;

import com.example.medical.module.chat.entity.AgentReviewEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentReviewEventRepository extends JpaRepository<AgentReviewEvent, Long> {

    List<AgentReviewEvent> findByRunIdOrderByIdAsc(Long runId);
}
