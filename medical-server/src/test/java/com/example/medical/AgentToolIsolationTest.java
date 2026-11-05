package com.example.medical;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Round 58.3 — the tool surface cannot be reached across runs, and a stale generation cannot submit.
 * <p>
 * A capability is bound to one run and one generation: using it on another run is a 403, using it
 * after the result was accepted is a 403, and a result for the wrong generation is a 409 that leaves
 * no row. The per-patient ownership of an appointment is covered by 58.1's test.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "app.agent.enabled=true",
        "app.agent.scheduling-enabled=false",
        "app.agent.inbound-token=test-inbound-token",
        "app.agent.outbound-token=test-outbound-token",
        "app.agent.poll-interval-ms=3600000",
        "app.agent.lease=120s",
        "app.agent.request-timeout=10s",
        "app.agent.deadline=30m",
        "app.agent.capability-ttl=5m",
})
class AgentToolIsolationTest extends IntegrationTestSupport {

    private static final HttpServer AGENT_STUB;
    private static final String BASE_URL;
    private static final List<String> CAPABILITIES = new CopyOnWriteArrayList<>();

    static {
        try {
            AGENT_STUB = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            AGENT_STUB.createContext("/internal/v1/runs", exchange -> {
                CAPABILITIES.add(exchange.getRequestHeaders().getFirst("X-Agent-Capability"));
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(202, -1);
                exchange.close();
            });
            AGENT_STUB.start();
            BASE_URL = "http://127.0.0.1:" + AGENT_STUB.getAddress().getPort();
        } catch (IOException e) {
            throw new IllegalStateException("could not start the agent stub", e);
        }
    }

    @DynamicPropertySource
    static void agentUrl(DynamicPropertyRegistry registry) {
        registry.add("app.agent.base-url", () -> BASE_URL);
    }

    @Autowired
    private com.example.medical.module.chat.service.AgentRunService runs;

    @Autowired
    private JdbcTemplate jdbc;

    private String patientToken;

    @BeforeAll
    void authenticate() throws Exception {
        patientToken = patientLogin("patient1", "patient123");
    }

    @AfterAll
    void stop() {
        AGENT_STUB.stop(0);
    }

    @BeforeEach
    void reset() {
        CAPABILITIES.clear();
        jdbc.update("DELETE FROM agent_review_event");
        jdbc.update("DELETE FROM agent_review_decision");
        jdbc.update("DELETE FROM agent_review_claim");
        jdbc.update("DELETE FROM agent_run_result");
        jdbc.update("DELETE FROM agent_run");
        jdbc.update("DELETE FROM message");
        jdbc.update("DELETE FROM chat_conversation");
    }

    @Test
    void aCapabilityFromAnotherRunIsRefused() throws Exception {
        Ready first = ready("When is my next appointment?");
        Ready second = ready("And the one after that?");

        mockMvc.perform(get("/api/v1/internal/agent/runs/" + second.runId + "/appointments")
                        .header("X-Agent-Service-Token", "test-inbound-token")
                        .header("X-Agent-Capability", first.capability))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/internal/agent/runs/" + first.runId + "/appointments")
                        .header("X-Agent-Service-Token", "test-inbound-token")
                        .header("X-Agent-Capability", first.capability))
                .andExpect(status().isOk());
    }

    @Test
    void aCapabilityIsRefusedAfterTheResultWasAccepted() throws Exception {
        Ready ready = ready("When is my next appointment?");
        submit(ready, 200);

        mockMvc.perform(get("/api/v1/internal/agent/runs/" + ready.runId + "/appointments")
                        .header("X-Agent-Service-Token", "test-inbound-token")
                        .header("X-Agent-Capability", ready.capability))
                .andExpect(status().isForbidden());
    }

    @Test
    void aResultForTheWrongGenerationIsRefusedAndLeavesNoRow() throws Exception {
        Ready ready = ready("When is my next appointment?");

        submit(ready, 409, ready.generation + 1);
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_run_result WHERE run_id = ?", Integer.class,
                ready.runId));

        submit(ready, 200);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_run_result WHERE run_id = ?", Integer.class,
                ready.runId));
    }

    @Test
    void afterATakeoverTheOldCapabilityIsRefusedAndTheNewOneWorks() throws Exception {
        Ready first = ready("When is my next appointment?");
        // Force the dispatcher to re-claim the run: a new attempt, and therefore a new generation
        // and a new capability (Round 58.3).
        jdbc.update("UPDATE agent_run SET state = 'PENDING', next_attempt_at = ?, "
                        + "lease_expires_at = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusSeconds(1)),
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusSeconds(1)),
                first.runId);
        runs.dispatchDue();

        String renewed = CAPABILITIES.get(CAPABILITIES.size() - 1);
        assertNotEquals(first.capability, renewed, "a re-dispatch issues a new capability");

        mockMvc.perform(get("/api/v1/internal/agent/runs/" + first.runId + "/appointments")
                        .header("X-Agent-Service-Token", "test-inbound-token")
                        .header("X-Agent-Capability", first.capability))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/internal/agent/runs/" + first.runId + "/appointments")
                        .header("X-Agent-Service-Token", "test-inbound-token")
                        .header("X-Agent-Capability", renewed))
                .andExpect(status().isOk());
    }

    // ── helpers ─────────────────────────────────────────────────────

    private record Ready(long runId, String capability, long generation) { }

    private Ready ready(String content) throws Exception {
        MvcResult sent = mockMvc.perform(post("/api/v1/patient/me/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("receiverId", 2, "content", content)))
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andReturn();
        long messageId = objectMapper.readTree(sent.getResponse().getContentAsString())
                .get("data").get("id").asLong();
        runs.dispatchDue();
        Long runId = jdbc.queryForObject(
                "SELECT id FROM agent_run WHERE message_id = ?", Long.class, messageId);
        long generation = jdbc.queryForObject(
                "SELECT execution_generation FROM agent_run WHERE id = ?", Long.class, runId);
        return new Ready(runId, CAPABILITIES.get(CAPABILITIES.size() - 1), generation);
    }

    private void submit(Ready ready, int expected) throws Exception {
        submit(ready, expected, ready.generation);
    }

    private void submit(Ready ready, int expected, long generation) throws Exception {
        mockMvc.perform(post("/api/v1/internal/agent/runs/" + ready.runId + "/results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contract_version", "v1", "run_id", ready.runId,
                                "generation", generation, "intent", "APPOINTMENT",
                                "risk", "ROUTINE", "requires_clinician", false,
                                "draft", "A member of the care team will reply.\n\n"
                                        + "Prepared for staff review; not medical advice.",
                                "citations", List.of(),
                                "validation", Map.of("ok", true, "kind", "routing_summary",
                                        "reasons", List.of(), "rejected_reasons", List.of()))))
                        .header("X-Agent-Service-Token", "test-inbound-token")
                        .header("X-Agent-Capability", ready.capability))
                .andExpect(status().is(expected));
    }
}
