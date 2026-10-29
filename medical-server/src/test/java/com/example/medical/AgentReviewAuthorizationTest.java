package com.example.medical;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Round 57.1 — the review queue and who may read it.
 * <p>
 * A draft is patient data: a patient token must not read the queue at all, a doctor must not see a
 * patient outside their scope, and one run is reviewed by one claimant. A message whose agent run
 * produced nothing stays in the queue as a human-only item, because an AI failure must never take a
 * question out of human hands.
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
class AgentReviewAuthorizationTest extends IntegrationTestSupport {

    private static final HttpServer AGENT_STUB;
    private static final String BASE_URL;
    private static final List<String> CAPABILITIES = new CopyOnWriteArrayList<>();
    private static volatile int stubStatus = 202;

    static {
        try {
            AGENT_STUB = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            AGENT_STUB.createContext("/internal/v1/runs", exchange -> {
                CAPABILITIES.add(exchange.getRequestHeaders().getFirst("X-Agent-Capability"));
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(stubStatus, -1);
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

    private static final long OTHER_DOCTOR_ID = 99L;

    @Autowired
    private com.example.medical.module.chat.service.AgentRunService runs;

    @MockitoSpyBean
    private com.example.medical.module.chat.repository.AgentRunResultRepository resultRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    private String patientToken;
    private String doctorToken;
    private String otherDoctorToken;

    @BeforeAll
    void authenticate() throws Exception {
        patientToken = patientLogin("patient1", "patient123");
        doctorToken = login("doctor1", "doctor123");
        seedOtherDoctor();
        otherDoctorToken = login("doctor2", "doctor123");
    }

    @AfterAll
    void stop() {
        AGENT_STUB.stop(0);
    }

    @BeforeEach
    void reset() {
        stubStatus = 202;
        CAPABILITIES.clear();
        jdbc.update("DELETE FROM agent_review_claim");
        jdbc.update("DELETE FROM message");
        jdbc.update("DELETE FROM chat_conversation");
        jdbc.update("DELETE FROM agent_run_result");
        jdbc.update("DELETE FROM agent_run");
    }

    // ── authorization ───────────────────────────────────────────────

    @Test
    void aPatientCannotReadTheReviewQueue() throws Exception {
        mockMvc.perform(get("/api/v1/agent/reviews")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void aDoctorOutsideThePatientsScopeNeitherSeesNorClaimsTheDraft() throws Exception {
        Dispatched dispatched = dispatchedRun("My invoice looks wrong");
        result(dispatched, "BILLING");

        mockMvc.perform(get("/api/v1/agent/reviews")
                        .header("Authorization", "Bearer " + otherDoctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(post("/api/v1/agent/reviews/" + dispatched.runId() + "/claim")
                        .header("Authorization", "Bearer " + otherDoctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void theReviewingDoctorSeesTheDraftAndIsTheOnlyClaimant() throws Exception {
        Dispatched dispatched = dispatchedRun("My invoice looks wrong");
        result(dispatched, "BILLING");

        mockMvc.perform(get("/api/v1/agent/reviews")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].runId").value(dispatched.runId()))
                .andExpect(jsonPath("$.data[0].intent").value("BILLING"))
                .andExpect(jsonPath("$.data[0].draft").value(
                        org.hamcrest.Matchers.containsString("instalments")))
                .andExpect(jsonPath("$.data[0].citations[0].documentId")
                        .value("billing-and-insurance"))
                .andExpect(jsonPath("$.data[0].requiresClinician").value(false));

        mockMvc.perform(post("/api/v1/agent/reviews/" + dispatched.runId() + "/claim")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value(dispatched.runId()))
                .andExpect(jsonPath("$.data.claimedBy").value(2))
                .andExpect(jsonPath("$.data.claimedByMe").value(true));

        mockMvc.perform(post("/api/v1/agent/reviews/" + dispatched.runId() + "/claim")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/agent/reviews/" + dispatched.runId() + "/claim")
                        .header("Authorization", "Bearer " + otherDoctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAgentFailureKeepsTheMessageAsAHumanOnlyItem() throws Exception {
        stubStatus = 503;
        Dispatched dispatched = dispatchedRun("Please help with my bill");
        // The dispatch failed, so the run is not waiting for review; the message is still owed an
        // answer and the queue must show it without a draft.
        mockMvc.perform(get("/api/v1/agent/reviews")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].draft").doesNotExist())
                .andExpect(jsonPath("$.data[0].reason").value("RUN_PENDING"));
    }

    @Test
    void anAdminSeesAndClaimsThreadsItIsNotPartOf() throws Exception {
        Dispatched dispatched = dispatchedRun("My invoice looks wrong");
        result(dispatched, "BILLING");
        String adminToken = login("admin", "admin123");

        mockMvc.perform(get("/api/v1/agent/reviews")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].runId").value(dispatched.runId()))
                .andExpect(jsonPath("$.data[0].patientId").value(100));

        mockMvc.perform(post("/api/v1/agent/reviews/" + dispatched.runId() + "/claim")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.claimedBy").value(1));
    }

    @Test
    void aBodyRunIdThatDoesNotMatchThePathIsRefused() throws Exception {
        Dispatched dispatched = dispatchedRun("My invoice looks wrong");
        long generation = jdbc.queryForObject(
                "SELECT execution_generation FROM agent_run WHERE id = ?", Long.class,
                dispatched.runId());

        mockMvc.perform(post("/api/v1/internal/agent/runs/" + dispatched.runId() + "/results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contract_version", "v1",
                                "run_id", dispatched.runId() + 1000,
                                "generation", generation,
                                "intent", "BILLING",
                                "risk", "ROUTINE",
                                "requires_clinician", false,
                                "draft", "A balance can be paid in instalments.",
                                "citations", List.of(),
                                "validation", Map.of("ok", true, "kind", "policy_answer",
                                        "reasons", List.of(), "rejected_reasons", List.of()))))
                        .header("X-Agent-Service-Token", "test-inbound-token")
                        .header("X-Agent-Capability", dispatched.capability()))
                .andExpect(status().isBadRequest());

        assertEquals("RUNNING", jdbc.queryForObject(
                "SELECT state FROM agent_run WHERE id = ?", String.class, dispatched.runId()));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_run_result WHERE run_id = ?", Integer.class,
                dispatched.runId()));
    }

    @Test
    void anAnsweredThreadLeavesTheQueue() throws Exception {
        Dispatched dispatched = dispatchedRun("My invoice looks wrong");
        result(dispatched, "BILLING");
        sendAsDoctor("We have corrected the invoice.");

        mockMvc.perform(get("/api/v1/agent/reviews")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void twoConcurrentClaimsByTheSameReviewerBothSucceedWithOneRow() throws Exception {
        Dispatched dispatched = dispatchedRun("My invoice looks wrong");
        result(dispatched, "BILLING");
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
        try {
            var first = pool.submit(() -> claim(doctorToken, dispatched.runId(), barrier));
            var second = pool.submit(() -> claim(doctorToken, dispatched.runId(), barrier));
            assertEquals(200, first.get(30, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(200, second.get(30, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_review_claim WHERE run_id = ?", Integer.class,
                dispatched.runId()));
    }

    @Test
    void twoLegitimateReviewersRacingProduceExactlyOneWinner() throws Exception {
        Dispatched dispatched = dispatchedRun("My invoice looks wrong");
        result(dispatched, "BILLING");
        String adminToken = login("admin", "admin123");
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
        java.util.List<Integer> statuses;
        try {
            var first = pool.submit(() -> claim(doctorToken, dispatched.runId(), barrier));
            var second = pool.submit(() -> claim(adminToken, dispatched.runId(), barrier));
            statuses = java.util.List.of(
                    first.get(30, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(30, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(java.util.List.of(200, 409), statuses.stream().sorted().toList(),
                "both reviewers are in scope, so exactly one wins and the other is a conflict");
        long winner = jdbc.queryForObject(
                "SELECT staff_id FROM agent_review_claim WHERE run_id = ?", Long.class,
                dispatched.runId());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_review_claim WHERE run_id = ?", Integer.class,
                dispatched.runId()));
        assertTrue(winner == 1L || winner == 2L, "the claim must belong to one of the racers");
    }

    @Test
    void claimingARunThatIsNoLongerTheLatestMessageIsRefusedWithoutAClaim() throws Exception {
        Dispatched first = dispatchedRun("My invoice looks wrong");
        result(first, "BILLING");
        // The patient writes again: the waiting run is now history, even though its result is
        // still there, so a claim must be refused and must not leave a row behind.
        mockMvc.perform(post("/api/v1/patient/me/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverId", 2, "content", "Also, I moved house.")))
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/agent/reviews/" + first.runId() + "/claim")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isConflict());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_review_claim WHERE run_id = ?", Integer.class,
                first.runId()));
    }

    @Test
    void claimingAfterAStaffReplyIsRefusedWithoutAClaim() throws Exception {
        Dispatched dispatched = dispatchedRun("My invoice looks wrong");
        result(dispatched, "BILLING");
        sendAsDoctor("We have corrected the invoice.");

        mockMvc.perform(post("/api/v1/agent/reviews/" + dispatched.runId() + "/claim")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isConflict());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_review_claim WHERE run_id = ?", Integer.class,
                dispatched.runId()));
    }

    private int claim(String token, long runId, java.util.concurrent.CyclicBarrier barrier)
            throws Exception {
        barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
        return mockMvc.perform(post("/api/v1/agent/reviews/" + runId + "/claim")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
    }

    private String otherDoctorTokenForClaim() throws Exception {
        return login("doctor2", "doctor123");
    }

    // ── helpers ─────────────────────────────────────────────────────

    @Test
    void aFailedResultSaveRollsBackTheCompletionAndCanBeRetried() throws Exception {
        Dispatched dispatched = dispatchedRun("My invoice looks wrong");
        long generation = jdbc.queryForObject(
                "SELECT execution_generation FROM agent_run WHERE id = ?", Long.class,
                dispatched.runId());
        String url = "/api/v1/internal/agent/runs/" + dispatched.runId() + "/results";
        java.util.Map<String, Object> body = body(dispatched.runId(), generation, List.of());

        org.mockito.Mockito.doThrow(
                        new org.springframework.dao.DataIntegrityViolationException("storage failed"))
                .when(resultRepository).save(org.mockito.ArgumentMatchers.any(
                        com.example.medical.module.chat.entity.AgentRunResult.class));
        try {
            postResult(url, body, dispatched.capability())
                    .andExpect(status().is5xxServerError());
        } finally {
            org.mockito.Mockito.reset(resultRepository);
        }

        // The status change must have rolled back with the failed save, so the capability can
        // still complete the run.
        assertEquals("RUNNING", jdbc.queryForObject(
                "SELECT state FROM agent_run WHERE id = ?", String.class, dispatched.runId()));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_run_result WHERE run_id = ?", Integer.class,
                dispatched.runId()));

        postResult(url, body, dispatched.capability()).andExpect(status().isOk());

        assertEquals("WAITING_REVIEW", jdbc.queryForObject(
                "SELECT state FROM agent_run WHERE id = ?", String.class, dispatched.runId()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_run_result WHERE run_id = ?", Integer.class,
                dispatched.runId()));
    }

    @Test
    void theHttpEndpointRefusesUnvalidatedResultBodies() throws Exception {
        Dispatched dispatched = dispatchedRun("My invoice looks wrong");
        long generation = jdbc.queryForObject(
                "SELECT execution_generation FROM agent_run WHERE id = ?", Long.class,
                dispatched.runId());
        String capability = dispatched.capability();
        String url = "/api/v1/internal/agent/runs/" + dispatched.runId() + "/results";

        // Each body is otherwise valid, so the 400 comes from the field under test.
        long runId = dispatched.runId();
        postResult(url, body(runId, generation, null), capability)
                .andExpect(status().isBadRequest());                       // citations: null
        postResult(url, body(runId, generation, java.util.Arrays.asList((Object) null)), capability)
                .andExpect(status().isBadRequest());                       // citation element null
        postResult(url, body(runId, generation, List.of(citation(null))), capability)
                .andExpect(status().isBadRequest());                       // heading_path: null
        postResult(url, body(runId, generation, List.of(citation(List.of("")))), capability)
                .andExpect(status().isBadRequest());                       // blank heading

        java.util.Map<String, Object> missingFlag = body(runId, generation, List.of());
        missingFlag.remove("requires_clinician");
        postResult(url, missingFlag, capability).andExpect(status().isBadRequest());

        java.util.Map<String, Object> missingOk = body(runId, generation, List.of());
        missingOk.put("validation", java.util.Map.of("kind", "policy_answer"));
        postResult(url, missingOk, capability).andExpect(status().isBadRequest());

        java.util.Map<String, Object> missingCitations = body(runId, generation, List.of());
        missingCitations.remove("citations");
        postResult(url, missingCitations, capability).andExpect(status().isBadRequest());

        java.util.Map<String, Object> missingHeading = body(runId, generation,
                List.of(citation(List.of())));
        java.util.Map<String, Object> headingless = new java.util.HashMap<>(citation(List.of()));
        headingless.remove("heading_path");
        postResult(url, body(runId, generation, List.of(headingless)), capability)
                .andExpect(status().isBadRequest());

        java.util.Map<String, Object> noReasons = body(runId, generation, List.of());
        noReasons.put("validation", java.util.Map.of("ok", true, "kind", "policy_answer",
                "rejected_reasons", List.of()));
        postResult(url, noReasons, capability).andExpect(status().isBadRequest());

        java.util.Map<String, Object> noRejected = body(runId, generation, List.of());
        noRejected.put("validation", java.util.Map.of("ok", true, "kind", "policy_answer",
                "reasons", List.of()));
        postResult(url, noRejected, capability).andExpect(status().isBadRequest());

        assertEquals("RUNNING", jdbc.queryForObject(
                "SELECT state FROM agent_run WHERE id = ?", String.class, dispatched.runId()));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_run_result WHERE run_id = ?", Integer.class,
                dispatched.runId()));
    }

    @Test
    void anEmptyCitationListIsAcceptedForARoutingSummary() throws Exception {
        Dispatched dispatched = dispatchedRun("I have chest pain");
        long generation = jdbc.queryForObject(
                "SELECT execution_generation FROM agent_run WHERE id = ?", Long.class,
                dispatched.runId());
        String url = "/api/v1/internal/agent/runs/" + dispatched.runId() + "/results";

        postResult(url, body(dispatched.runId(), generation, List.of()), dispatched.capability())
                .andExpect(status().isOk());

        assertEquals("WAITING_REVIEW", jdbc.queryForObject(
                "SELECT state FROM agent_run WHERE id = ?", String.class, dispatched.runId()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_run_result WHERE run_id = ?", Integer.class,
                dispatched.runId()));
    }

    private org.springframework.test.web.servlet.ResultActions postResult(
            String url, java.util.Map<String, Object> body, String capability) throws Exception {
        return mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("X-Agent-Service-Token", "test-inbound-token")
                        .header("X-Agent-Capability", capability));
    }

    private java.util.Map<String, Object> body(long runId, long generation, Object citations) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("contract_version", "v1");
        body.put("run_id", runId);
        body.put("generation", generation);
        body.put("intent", "BILLING");
        body.put("risk", "ROUTINE");
        body.put("requires_clinician", false);
        body.put("draft", "A balance can be paid in instalments.");
        body.put("citations", citations);
        body.put("validation", java.util.Map.of("ok", true, "kind", "policy_answer",
                "reasons", List.of(), "rejected_reasons", List.of()));
        return body;
    }

    private java.util.Map<String, Object> citation(List<String> headingPath) {
        java.util.Map<String, Object> citation = new java.util.HashMap<>();
        citation.put("chunk_id", "billing-and-insurance:1:002");
        citation.put("document_id", "billing-and-insurance");
        citation.put("document_version", 1L);
        citation.put("title", "Billing and insurance policy");
        citation.put("heading_path", headingPath);
        citation.put("content_hash", "a".repeat(64));
        return citation;
    }

    private void seedOtherDoctor() {
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id = ?",
                Integer.class, OTHER_DOCTOR_ID);
        if (existing != null && existing > 0) return;
        jdbc.update("INSERT INTO sys_user (id, username, password, real_name, status, is_deleted, "
                        + "create_time, update_time, version) VALUES (?,?,?,?,1,0,CURRENT_TIMESTAMP,"
                        + "CURRENT_TIMESTAMP,0)",
                OTHER_DOCTOR_ID, "doctor2",
                passwordEncoder.encode("doctor123"), "Dr. Other");
        jdbc.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?,?)",
                OTHER_DOCTOR_ID, 2L);
    }

    private Dispatched dispatchedRun(String content) throws Exception {
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
        Long runId = jdbc.query("SELECT id FROM agent_run WHERE message_id = ?",
                rs -> rs.next() ? rs.getLong(1) : null, messageId);
        String capability = CAPABILITIES.isEmpty() ? null : CAPABILITIES.get(CAPABILITIES.size() - 1);
        return new Dispatched(messageId, runId == null ? 0L : runId, capability);
    }

    /** Submits the result the worker would submit, so the run reaches WAITING_REVIEW. */
    private void result(Dispatched dispatched, String intent) throws Exception {
        assertNotNull(dispatched.runId());
        long generation = jdbc.queryForObject(
                "SELECT execution_generation FROM agent_run WHERE id = ?", Long.class,
                dispatched.runId());
        mockMvc.perform(post("/api/v1/internal/agent/runs/" + dispatched.runId() + "/results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contract_version", "v1",
                                "run_id", dispatched.runId(),
                                "generation", generation,
                                "intent", intent,
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
                        .header("X-Agent-Capability", dispatched.capability()))
                .andExpect(status().isOk());
    }

    private void sendAsDoctor(String content) throws Exception {
        mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverId", 100, "receiverType", "PATIENT",
                                "content", content)))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());
    }

    private record Dispatched(long messageId, long runId, String capability) { }
}
