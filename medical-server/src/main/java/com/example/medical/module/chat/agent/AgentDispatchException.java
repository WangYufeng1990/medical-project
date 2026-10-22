package com.example.medical.module.chat.agent;

/**
 * A dispatch failure reduced to a category (Round 55.2 review).
 * <p>
 * What the agent service answered is not carried: an HTTP error body may echo the input
 * it rejected, and that input is patient text. The queue stores and logs the category —
 * enough to tell a timeout from a 422 — and nothing the remote side wrote.
 */
public class AgentDispatchException extends RuntimeException {

    private final String category;

    public AgentDispatchException(String category, String message) {
        super(message);
        this.category = category;
    }

    /** e.g. {@code AGENT_HTTP_422}, {@code AGENT_UNREACHABLE}, {@code AGENT_CLIENT_ERROR}. */
    public String category() {
        return category;
    }
}
