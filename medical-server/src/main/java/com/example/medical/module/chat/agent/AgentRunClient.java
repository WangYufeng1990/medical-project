package com.example.medical.module.chat.agent;

import com.example.medical.module.chat.dto.agent.AgentRunStartRequest;

/** The outbound call to the agent service; an exception means "try again later". */
public interface AgentRunClient {

    /**
     * @param capability the short-lived token of this attempt, presented to the service so
     *                   it can prove which run and generation it acts for (Round 55.3)
     */
    void start(AgentRunStartRequest request, String capability);
}
