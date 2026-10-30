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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Round 57.3 — freshness and recovery.
 * <p>
 * A draft is approved against the conversation it was written for: a newer patient message or a
 * human reply makes it stale, and approving it must fail without publishing. A result that arrives
 * after publication is refused, so recovery can never publish a second reply.
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
class ReviewConcurrencyIntegrationTest extends IntegrationTestSupport {

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

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

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
    void aNewerPatientMessagePreventsTheStaleApproval() throws Exception {
        Ready ready = ready("My invoice looks wrong");
        claim(ready.runId());

        // The patient writes again after the draft: approving would answer a question the draft
        // never saw.
        patientSends("Also, I moved house last week.");

        mockMvc.perform(post("/api/v1/agent/reviews/" + ready.runId() + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isConflict());

        assertEquals(0, countStaffMessages());
        assertEquals("WAITING_REVIEW", jdbc.queryForObject(
                "SELECT state FROM agent_run WHERE id = ?", String.class, ready.runId()));
    }

    @Test
    void aHumanReplySupersedesTheWaitingDraft() throws Exception {
        Ready ready = ready("My invoice looks wrong");

        doctorSends("We are checking your invoice and will call you.");

        assertEquals("SUPERSEDED", jdbc.queryForObject(
                "SELECT state FROM agent_run WHERE id = ?", String.class, ready.runId()));
        mockMvc.perform(post("/api/v1/agent/reviews/" + ready.runId() + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isConflict());
        assertEquals(1, countStaffMessages());
    }

    @Test
    void aResultArrivingAfterPublicationIsRefusedAndPublishesNothing() throws Exception {
        Ready ready = ready("My invoice looks wrong");
        claim(ready.runId());
        mockMvc.perform(post("/api/v1/agent/reviews/" + ready.runId() + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());
        assertEquals(1, countStaffMessages());

        // Recovery replays the submission for a run that is already published: the capability was
        // revoked when the result was accepted, so the refusal is a 403 here (a still-valid one
        // would reach the state check and be a 409). Either way nothing may be published again.
        replayResult(ready);

        assertEquals(1, countStaffMessages());
        assertEquals("PUBLISHED", jdbc.queryForObject(
                "SELECT state FROM agent_run WHERE id = ?", String.class, ready.runId()));
    }

    @Test
    void aHumanReplyOnlySupersedesThatConversationsDraft() throws Exception {
        Ready ready = ready("My invoice looks wrong");
        claim(ready.runId());

        // A different clinician (the admin, id 1) answers the patient in their own thread.
        adminSends("Your statement is on its way.");

        assertEquals("WAITING_REVIEW", jdbc.queryForObject(
                "SELECT state FROM agent_run WHERE id = ?", String.class, ready.runId()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_review_claim WHERE run_id = ?", Integer.class,
                ready.runId()));

        // The draft's own clinician answers: now it is superseded.
        doctorSends("We are checking your invoice.");
        assertEquals("SUPERSEDED", jdbc.queryForObject(
                "SELECT state FROM agent_run WHERE id = ?", String.class, ready.runId()));
        // One reply in each thread.
        assertEquals(2, countStaffMessages());
    }

    @Test
    void anApprovalRacingAManualReplyDoesNotDeadlock() throws Exception {
        Ready ready = ready("My invoice looks wrong");
        claim(ready.runId());
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
        int approveStatus;
        int sendStatus;
        try {
            var approving = pool.submit(() -> {
                barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/v1/agent/reviews/" + ready.runId() + "/approve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                                .header("Authorization", "Bearer " + doctorToken))
                        .andReturn().getResponse().getStatus();
            });
            var sending = pool.submit(() -> {
                barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/v1/messages")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "receiverId", 100, "receiverType", "PATIENT",
                                        "content", "A human answer.")))
                                .header("Authorization", "Bearer " + doctorToken))
                        .andReturn().getResponse().getStatus();
            });
            approveStatus = approving.get(30, java.util.concurrent.TimeUnit.SECONDS);
            sendStatus = sending.get(30, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        // The lock order is conversation then run on both paths, so one completes first and the
        // other observes its effect; neither may fail with a server error.
        assertEquals(200, sendStatus);
        String state = jdbc.queryForObject(
                "SELECT state FROM agent_run WHERE id = ?", String.class, ready.runId());
        if (approveStatus == 200) {
            // Approval won the lock: it published, then the manual reply was stored as well.
            assertEquals("PUBLISHED", state);
            assertEquals(2, countStaffMessages());
        } else {
            // The manual reply won: the draft is stale and approving it is a conflict.
            assertEquals(409, approveStatus);
            assertEquals("SUPERSEDED", state);
            assertEquals(1, countStaffMessages());
        }
    }

    @Test
    void aPatientMessageWaitsForTheThreadLockAndStillBecomesTheLastWord() throws Exception {
        Ready ready = ready("My invoice looks wrong");

        // Hold the thread lock as an approval would, in a separate transaction.
        java.util.concurrent.CountDownLatch locked = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        Thread holder = new Thread(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbc.queryForObject("SELECT id FROM chat_conversation WHERE "
                    + "((party_a_type = 'STAFF' AND party_a_id = 2 AND party_b_type = 'PATIENT' "
                    + "AND party_b_id = 100) OR (party_a_type = 'PATIENT' AND party_a_id = 100 "
                    + "AND party_b_type = 'STAFF' AND party_b_id = 2)) FOR UPDATE", Long.class);
            locked.countDown();
            try {
                release.await(20, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        holder.start();
        assertTrue(locked.await(10, java.util.concurrent.TimeUnit.SECONDS));
        int before = countAllMessages();

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var sending = pool.submit(() -> mockMvc.perform(post("/api/v1/patient/me/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "receiverId", 2, "content", "One more thing about my bill.")))
                            .header("Authorization", "Bearer " + patientToken))
                    .andReturn().getResponse().getStatus());

            // While the lock is held the send must not have created its message yet.
            Thread.sleep(300);
            assertEquals(before, countAllMessages(),
                    "a message must not be inserted before the thread lock is acquired");

            release.countDown();
            assertEquals(200, sending.get(30, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            release.countDown();
            pool.shutdownNow();
            holder.join(10_000);
        }

        // The patient's message is the conversation's last word again, and the draft's own message
        // is no longer the latest, so the thread is pending review rather than answered.
        Long lastMessageId = jdbc.queryForObject("SELECT MAX(id) FROM message", Long.class);
        String lastSender = jdbc.queryForObject(
                "SELECT sender_type FROM message WHERE id = ?", String.class, lastMessageId);
        assertEquals("PATIENT", lastSender, "the blocked send must be the thread's last word");
        assertTrue(lastMessageId > ready.messageId());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/agent/reviews")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data[0].messageId").value(lastMessageId))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data[0].draft").doesNotExist());
    }

    private int countAllMessages() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM message", Integer.class);
    }

    // ── helpers ─────────────────────────────────────────────────────

    private record Ready(long messageId, long runId, String capability, long generation) { }

    private void claim(long runId) throws Exception {
        mockMvc.perform(post("/api/v1/agent/reviews/" + runId + "/claim")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());
    }

    private void patientSends(String content) throws Exception {
        mockMvc.perform(post("/api/v1/patient/me/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("receiverId", 2, "content", content)))
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk());
    }

    private void adminSends(String content) throws Exception {
        String adminToken = login("admin", "admin123");
        mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverId", 100, "receiverType", "PATIENT",
                                "content", content)))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    private void doctorSends(String content) throws Exception {
        mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverId", 100, "receiverType", "PATIENT",
                                "content", content)))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());
    }

    private int countStaffMessages() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM message WHERE receiver_id = 100 AND sender_type = 'STAFF'",
                Integer.class);
    }

    private void replayResult(Ready ready) throws Exception {
        mockMvc.perform(post("/api/v1/internal/agent/runs/" + ready.runId() + "/results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contract_version", "v1",
                                "run_id", ready.runId(),
                                "generation", ready.generation(),
                                "intent", "BILLING",
                                "risk", "ROUTINE",
                                "requires_clinician", false,
                                "draft", "A balance can be paid in instalments.",
                                "citations", List.of(),
                                "validation", Map.of("ok", true, "kind", "policy_answer",
                                        "reasons", List.of(), "rejected_reasons", List.of()))))
                        .header("X-Agent-Service-Token", "test-inbound-token")
                        .header("X-Agent-Capability", ready.capability()))
                .andExpect(status().is4xxClientError());
    }

    private void submitResult(Ready ready, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/internal/agent/runs/" + ready.runId() + "/results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contract_version", "v1",
                                "run_id", ready.runId(),
                                "generation", ready.generation(),
                                "intent", "BILLING",
                                "risk", "ROUTINE",
                                "requires_clinician", false,
                                "draft", "A balance can be paid in instalments.",
                                "citations", List.of(),
                                "validation", Map.of("ok", true, "kind", "policy_answer",
                                        "reasons", List.of(), "rejected_reasons", List.of()))))
                        .header("X-Agent-Service-Token", "test-inbound-token")
                        .header("X-Agent-Capability", ready.capability()))
                .andExpect(status().is(expectedStatus));
    }

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
        String capability = CAPABILITIES.get(CAPABILITIES.size() - 1);
        long generation = jdbc.queryForObject(
                "SELECT execution_generation FROM agent_run WHERE id = ?", Long.class, runId);
        Ready ready = new Ready(messageId, runId, capability, generation);
        submitResult(ready, 200);
        return ready;
    }
}
