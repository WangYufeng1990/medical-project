package com.example.medical.module.chat.dto.agent;

import com.example.medical.module.chat.entity.AgentRun;
import com.example.medical.module.chat.entity.Message;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.UUID;

/**
 * The Java side of the start request (Round 55.2), field for field the contract in
 * {@code contracts/agent/v1/run-start.request.schema.json}.
 */
@JsonPropertyOrder({"contract_version", "run_id", "message_id", "patient_id",
        "conversation_revision", "sender_type", "execution_generation", "content", "locale",
        "request_id"})
public record AgentRunStartRequest(
        @JsonProperty("contract_version") String contractVersion,
        @JsonProperty("run_id") Long runId,
        @JsonProperty("message_id") Long messageId,
        @JsonProperty("patient_id") Long patientId,
        @JsonProperty("conversation_revision") Long conversationRevision,
        @JsonProperty("sender_type") String senderType,
        @JsonProperty("execution_generation") Long executionGeneration,
        @JsonProperty("content") String content,
        @JsonProperty("locale") String locale,
        @JsonProperty("request_id") String requestId) {

    /** The contract bounds the text the model sees; the full message stays in Java. */
    private static final int MAX_CONTENT = 4000;

    /**
     * One stable request id per (run, generation): a retry of the same attempt is
     * recognisable to the receiving service, while a reclaimed lease is a new generation.
     */
    public static AgentRunStartRequest of(AgentRun run, Message message) {
        String text = message.getContent() == null ? "" : message.getContent();
        if (text.codePointCount(0, text.length()) > MAX_CONTENT) {
            text = text.substring(0, text.offsetByCodePoints(0, MAX_CONTENT));
        }
        return new AgentRunStartRequest(
                "v1",
                run.getId(),
                run.getMessageId(),
                run.getPatientId(),
                run.getConversationRevision(),
                "PATIENT",
                run.getExecutionGeneration(),
                text,
                "en",
                UUID.nameUUIDFromBytes(("agent-run:" + run.getId() + ":g"
                        + run.getExecutionGeneration()).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .toString());
    }
}
