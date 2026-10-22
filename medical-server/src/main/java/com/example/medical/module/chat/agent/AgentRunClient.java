package com.example.medical.module.chat.agent;

import com.example.medical.module.chat.dto.agent.AgentRunStartRequest;

/** The outbound call to the agent service; an exception means "try again later". */
public interface AgentRunClient {

    void start(AgentRunStartRequest request);
}
