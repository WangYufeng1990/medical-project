package com.example.medical.module.chat.service;

import com.example.medical.module.chat.entity.AgentReviewEvent;
import com.example.medical.module.chat.repository.AgentReviewEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes review events; a refused action must survive the transaction that refused it. */
@Service
@RequiredArgsConstructor
public class AgentReviewEventService {

    private final AgentReviewEventRepository events;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long runId, String action, Long actorId, String detail) {
        AgentReviewEvent event = new AgentReviewEvent();
        event.setRunId(runId);
        event.setAction(action);
        event.setActorId(actorId);
        event.setDetail(detail);
        events.save(event);
    }
}
