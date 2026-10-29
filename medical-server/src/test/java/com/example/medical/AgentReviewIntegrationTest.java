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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Round 57.2 — approve, edit, reject and publish.
 * <p>
 * The demo's promise is that a human decides and the patient receives exactly one reply: the
 * original draft and the published text are both recorded, an edit is what the patient sees, and a
 * refused draft leaves the message waiting for a human.
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
class AgentReviewIntegrationTest extends IntegrationTestSupport {

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
    private String doctorToken;

    @BeforeAll
    void authenticate() throws Exception {
        patientToken = patientLogin("patient1", "patient123");
        doctorToken = login("doctor1", "doctor123");
    }

    @AfterAll
    void stop() {
        AGENT_STUB.stop(0);
    }

    @BeforeEach
    void reset() {
        CAPABILITIES.clear();
        jdbc.update("DELETE FROM agent_review_decision");
        jdbc.update("DELETE FROM agent_review_claim");
        jdbc.update("DELETE FROM agent_run_result");
        jdbc.update("DELETE FROM agent_run");
        jdbc.update("DELETE FROM message");
        jdbc.update("DELETE FROM chat_conversation");
    }

    @Test
    void anApprovedDraftIsPublishedOnceAndRecordsTheDraftAndTheSender() throws Exception {
        Dispatched dispatched = ready("My invoice looks wrong");
        claim(dispatched.runId());

        mockMvc.perform(post("/api/v1/agent/reviews/" + dispatched.runId() + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision").value("APPROVED"))
                .andExpect(jsonPath("$.data.actorId").value(2))
                .andExpect(jsonPath("$.data.publishedMessageId").exists())
                .andExpect(jsonPath("$.data.finalText").value(
                        org.hamcrest.Matchers.containsString("instalments")));

        assertEquals("PUBLISHED", jdbc.queryForObject(
                "SELECT state FROM agent_run WHERE id = ?", String.class, dispatched.runId()));
        Map<String, Object> decision = jdbc.queryForMap(
                "SELECT decision, actor_id, published_message_id, edited, final_text "
                        + "FROM agent_review_decision WHERE run_id = ?", dispatched.runId());
        assertEquals("APPROVED", decision.get("decision"));
        assertEquals(2L, ((Number) decision.get("actor_id")).longValue());
        assertNotNull(decision.get("published_message_id"));
        // The staff-facing line must not reach the patient, and the patient-facing one must.
        String finalText = com.example.medical.common.config.AesCryptoUtil.decrypt(
                (String) decision.get("final_text"));
        assertFalse(finalText.contains("Prepared for staff review"));
        assertTrue(finalText.contains("Nothing sent here is medical advice until a clinician has reviewed it"));

        assertEquals(1, countStaffMessages(100));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_review_decision WHERE run_id = ?", Integer.class,
                dispatched.runId()));
    }

    @Test
    void theEditedTextIsWhatThePatientReceives() throws Exception {
        Dispatched dispatched = ready("My invoice looks wrong");
        claim(dispatched.runId());
        String edit = "Thanks for flagging this - the billing office will call you today.";

        mockMvc.perform(post("/api/v1/agent/reviews/" + dispatched.runId() + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("finalText", edit)))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.edited").value(true));

        String published = readStaffMessage(100);
        assertTrue(published.startsWith(edit), "the edit is the published content: " + published);
        assertFalse(published.contains("Prepared for staff review"));
        assertTrue(published.contains("Nothing sent here is medical advice until a clinician has reviewed it"));
        // The original draft is still there for the record.
        assertTrue(com.example.medical.common.config.AesCryptoUtil.decrypt(jdbc.queryForObject(
                "SELECT draft FROM agent_run_result WHERE run_id = ?", String.class,
                dispatched.runId())).contains("instalments"));
    }

    @Test
    void concurrentApprovalsPublishExactlyOneReply() throws Exception {
        Dispatched dispatched = ready("My invoice looks wrong");
        claim(dispatched.runId());
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
        List<Integer> statuses;
        try {
            var first = pool.submit(() -> approve(doctorToken, dispatched.runId(), barrier));
            var second = pool.submit(() -> approve(doctorToken, dispatched.runId(), barrier));
            statuses = List.of(
                    first.get(30, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(30, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(List.of(200, 409), statuses.stream().sorted().toList());
        assertEquals(1, countStaffMessages(100));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_review_decision WHERE run_id = ?", Integer.class,
                dispatched.runId()));
    }

    @Test
    void aRejectedDraftStaysActionableForAHuman() throws Exception {
        Dispatched dispatched = ready("My invoice looks wrong");
        claim(dispatched.runId());

        mockMvc.perform(post("/api/v1/agent/reviews/" + dispatched.runId() + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("reason", "The invoice question needs a human review")))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision").value("REJECTED"));

        assertEquals("REJECTED", jdbc.queryForObject(
                "SELECT state FROM agent_run WHERE id = ?", String.class, dispatched.runId()));
        assertEquals(0, countStaffMessages(100));
        // The patient's question is still owed an answer, and the queue says so without a draft.
        mockMvc.perform(get("/api/v1/agent/reviews")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].reason").value("REJECTED"))
                .andExpect(jsonPath("$.data[0].draft").doesNotExist());

        mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverId", 100, "receiverType", "PATIENT",
                                "content", "We are checking the invoice and will call you.")))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/agent/reviews")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void aDecisionOnARunThatIsNotWaitingIsRefused() throws Exception {
        Dispatched dispatched = ready("My invoice looks wrong");
        claim(dispatched.runId());
        mockMvc.perform(post("/api/v1/agent/reviews/" + dispatched.runId() + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "no")))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/agent/reviews/" + dispatched.runId() + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isConflict());
        assertEquals(0, countStaffMessages(100));
    }

    @Test
    void aPatientCannotDecide() throws Exception {
        Dispatched dispatched = ready("My invoice looks wrong");
        claim(dispatched.runId());

        mockMvc.perform(post("/api/v1/agent/reviews/" + dispatched.runId() + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnclaimedRunCannotBeDecided() throws Exception {
        Dispatched dispatched = ready("My invoice looks wrong");

        mockMvc.perform(post("/api/v1/agent/reviews/" + dispatched.runId() + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isConflict());

        assertEquals("WAITING_REVIEW", jdbc.queryForObject(
                "SELECT state FROM agent_run WHERE id = ?", String.class, dispatched.runId()));
        assertEquals(0, countStaffMessages(100));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_review_decision WHERE run_id = ?", Integer.class,
                dispatched.runId()));
    }

    @Test
    void aColleagueCannotDecideOverTheClaimant() throws Exception {
        Dispatched dispatched = ready("My invoice looks wrong");
        claim(dispatched.runId());
        String adminToken = login("admin", "admin123");

        mockMvc.perform(post("/api/v1/agent/reviews/" + dispatched.runId() + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/agent/reviews/" + dispatched.runId() + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "no")))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        assertEquals("WAITING_REVIEW", jdbc.queryForObject(
                "SELECT state FROM agent_run WHERE id = ?", String.class, dispatched.runId()));
        assertEquals(0, countStaffMessages(100));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_review_decision WHERE run_id = ?", Integer.class,
                dispatched.runId()));

        // The claimant still can.
        mockMvc.perform(post("/api/v1/agent/reviews/" + dispatched.runId() + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());
    }

    @Test
    void approvingWithoutAChangeIsNotAnEdit() throws Exception {
        Dispatched dispatched = ready("My invoice looks wrong");
        claim(dispatched.runId());

        mockMvc.perform(post("/api/v1/agent/reviews/" + dispatched.runId() + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.edited").value(false));

        assertEquals(0, jdbc.queryForObject(
                "SELECT edited FROM agent_review_decision WHERE run_id = ?", Integer.class,
                dispatched.runId()));
    }

    @Test
    void theDecisionTableDoesNotStorePatientTextInTheClear() throws Exception {
        Dispatched dispatched = ready("My invoice looks wrong");
        claim(dispatched.runId());
        String marker = "Zxqv-sensitive-marker";
        mockMvc.perform(post("/api/v1/agent/reviews/" + dispatched.runId() + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("reason", marker + " needs a human")))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());

        String storedReason = jdbc.queryForObject(
                "SELECT reason FROM agent_review_decision WHERE run_id = ?", String.class,
                dispatched.runId());
        assertFalse(storedReason.contains(marker), "the stored reason must be ciphertext");
        assertTrue(com.example.medical.common.config.AesCryptoUtil.decrypt(storedReason)
                .contains(marker));

        // And the approved text is encrypted too.
        Dispatched second = ready("My invoice looks wrong");
        claim(second.runId());
        mockMvc.perform(post("/api/v1/agent/reviews/" + second.runId() + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("finalText", marker + " will call you")))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());
        String storedFinal = jdbc.queryForObject(
                "SELECT final_text FROM agent_review_decision WHERE run_id = ?", String.class,
                second.runId());
        assertFalse(storedFinal.contains(marker), "the final text must be ciphertext");
        assertTrue(com.example.medical.common.config.AesCryptoUtil.decrypt(storedFinal)
                .contains(marker));
    }

    // ── helpers ─────────────────────────────────────────────────────

    private record Dispatched(long messageId, long runId, String capability) { }

    private int approve(String token, long runId, java.util.concurrent.CyclicBarrier barrier)
            throws Exception {
        barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
        return mockMvc.perform(post("/api/v1/agent/reviews/" + runId + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
    }

    /** Message content is PHI-encrypted at rest, so read it back through the cipher. */
    private String readStaffMessage(long patientId) {
        String stored = jdbc.queryForObject(
                "SELECT content FROM message WHERE receiver_id = ? AND sender_type = 'STAFF' "
                        + "ORDER BY id DESC LIMIT 1", String.class, patientId);
        return com.example.medical.common.config.AesCryptoUtil.decrypt(stored);
    }

    private int countStaffMessages(long patientId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM message WHERE receiver_id = ? AND sender_type = 'STAFF'",
                Integer.class, patientId);
    }

    /** Claims the run as the reviewing doctor, which every decision now requires. */
    private void claim(long runId) throws Exception {
        mockMvc.perform(post("/api/v1/agent/reviews/" + runId + "/claim")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());
    }

    private Dispatched ready(String content) throws Exception {
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
        String capability = CAPABILITIES.get(CAPABILITIES.size() - 1);
        long generation = jdbc.queryForObject(
                "SELECT execution_generation FROM agent_run WHERE id = ?", Long.class, runId);

        mockMvc.perform(post("/api/v1/internal/agent/runs/" + runId + "/results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contract_version", "v1",
                                "run_id", runId,
                                "generation", generation,
                                "intent", "BILLING",
                                "risk", "ROUTINE",
                                "requires_clinician", false,
                                "draft", "A balance can be paid in instalments by agreement "
                                        + "[chunk:billing-and-insurance:1:002].\n\n"
                                        + "Prepared for staff review; not medical advice.",
                                "citations", List.of(Map.of(
                                        "chunk_id", "billing-and-insurance:1:002",
                                        "document_id", "billing-and-insurance",
                                        "document_version", 1,
                                        "title", "Billing and insurance policy",
                                        "heading_path", List.of("Payment plans"),
                                        "content_hash", "a".repeat(64))),
                                "validation", Map.of("ok", true, "kind", "policy_answer",
                                        "reasons", List.of(), "rejected_reasons", List.of()))))
                        .header("X-Agent-Service-Token", "test-inbound-token")
                        .header("X-Agent-Capability", capability))
                .andExpect(status().isOk());
        return new Dispatched(messageId, runId, capability);
    }
}
