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

    /**
     * A successful decision's event is part of that decision: if the decision rolls back, the event
     * must roll back with it, or the log claims an approval that never happened (Round 57.3 review).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordDecision(Long runId, String action, Long actorId) {
        save(runId, action, actorId, null);
    }

    /**
     * A refused or failed action has no outer transaction to join (it may have rolled back), so its
     * event is written on its own. The detail is a fixed classification, never the exception text or
     * the rejection reason: those live in the encrypted decision row (Round 57.3 review).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRefusal(Long runId, String action, Long actorId, String classification) {
        save(runId, action, actorId, classification);
    }

    private void save(Long runId, String action, Long actorId, String detail) {
        AgentReviewEvent event = new AgentReviewEvent();
        event.setRunId(runId);
        event.setAction(action);
        event.setActorId(actorId);
        event.setDetail(detail);
        events.save(event);
    }
}
