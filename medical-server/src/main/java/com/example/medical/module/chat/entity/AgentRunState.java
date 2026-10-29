package com.example.medical.module.chat.entity;

/**
 * The durable states a dispatch row can be in (Round 55.2). The vocabulary matches the
 * wire contract's {@code RunState}: the Python side answers with those names, so a stored
 * row and a reported status cannot disagree about what a state is called.
 */
public enum AgentRunState {

    PENDING,
    RUNNING,
    WAITING_REVIEW,
    COMPLETED,
    FAILED,
    CANCELLED,
    SUPERSEDED,
    /** A reviewer approved the draft and the reply was published (Round 57.2). */
    PUBLISHED,
    /** A reviewer refused the draft; the patient's message is still owed an answer by hand. */
    REJECTED;

    /** The names the contract uses, for assertions and error messages. */
    public static boolean isKnown(String value) {
        for (AgentRunState state : values()) {
            if (state.name().equals(value)) return true;
        }
        return false;
    }
}
