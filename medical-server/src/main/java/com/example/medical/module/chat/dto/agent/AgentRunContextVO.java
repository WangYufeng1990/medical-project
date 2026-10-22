package com.example.medical.module.chat.dto.agent;

import com.example.medical.module.chat.entity.AgentRun;

/** What a worker may learn about its run — server-owned identity only (Round 55.3). */
public record AgentRunContextVO(Long runId, Long patientId, Long conversationRevision,
                                String state, Long generation) {

    public static AgentRunContextVO of(AgentRun run) {
        return new AgentRunContextVO(run.getId(), run.getPatientId(),
                run.getConversationRevision(), run.getState(), run.getExecutionGeneration());
    }
}
