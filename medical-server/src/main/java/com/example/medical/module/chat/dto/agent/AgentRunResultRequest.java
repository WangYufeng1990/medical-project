package com.example.medical.module.chat.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * A result submission (Round 55.3). Provisional: the draft contract and review decision
 * arrive in Round 57, but the authorization checks around it are final now.
 */
@Data
public class AgentRunResultRequest {

    /** The generation the worker was given; a stale worker's number no longer matches. */
    @NotNull
    private Long generation;

    @NotBlank
    @Size(max = 64)
    private String outcome;
}
