package com.example.medical;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Round 55.3 — service and task authorization.
 * <p>
 * Two credentials with different meanings: the directional service token names the calling
 * process, the capability names the run and generation it may act on. The tests walk the
 * ways a worker can be wrong — no token, a forged token, another run's capability, an expired
 * one, one from a previous generation, one whose run has moved on, and one whose patient is
 * no longer active — and the allowlist property: the service token alone cannot read patient
 * records, and only a digest of the capability is ever stored.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "app.agent.enabled=true",
        "app.agent.scheduling-enabled=false",
        "app.agent.inbound-token=" + AgentExecutionAuthorizationTest.INBOUND_TOKEN,
        "app.agent.outbound-token=" + AgentExecutionAuthorizationTest.OUTBOUND_TOKEN,
        "app.agent.poll-interval-ms=3600000",
        "app.agent.lease=60s",
        "app.agent.capability-ttl=2m",
        "app.agent.request-timeout=5s",
})
class AgentExecutionAuthorizationTest extends IntegrationTestSupport {

    static final String INBOUND_TOKEN = "test-agent-inbound-token";
    static final String OUTBOUND_TOKEN = "test-agent-outbound-token";

    private static final HttpServer AGENT_STUB;
    private static final String BASE_URL;
    /** The capability header of each dispatch, in order. */
    private static final List<String> CAPABILITIES = new CopyOnWriteArrayList<>();
    /** The Authorization header of each dispatch, in order. */
    private static final List<String> AUTHORIZATIONS = new CopyOnWriteArrayList<>();

    static {
        try {
            AGENT_STUB = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            AGENT_STUB.createContext("/internal/v1/runs", exchange -> {
                String capability = exchange.getRequestHeaders().getFirst("X-Agent-Capability");
                String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                CAPABILITIES.add(capability == null ? "" : capability);
                AUTHORIZATIONS.add(authorization == null ? "" : authorization);
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
    static void agentServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("app.agent.base-url", () -> BASE_URL);
    }

    private String patientToken;

    @BeforeAll
    void authenticate() throws Exception {
        patientToken = patientLogin("patient1", "patient123");
    }

    @AfterAll
    void stopStub() {
        AGENT_STUB.stop(0);
    }

    @BeforeEach
    void clearQueue() {
        jdbcTemplate.update("DELETE FROM agent_capability");
        jdbcTemplate.update("DELETE FROM agent_run");
        CAPABILITIES.clear();
        AUTHORIZATIONS.clear();
    }

    // ── the two credentials ─────────────────────────────────────────

    @Test
    void aCallWithoutTheServiceTokenIsRejected() throws Exception {
        Dispatched dispatched = dispatchedRun("Message with a dispatched run");

        mockMvc.perform(get("/api/v1/internal/agent/runs/" + dispatched.runId())
                        .header("X-Agent-Capability", dispatched.capability()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aWrongServiceTokenIsRejected() throws Exception {
        Dispatched dispatched = dispatchedRun("Message with a dispatched run");

        mockMvc.perform(get("/api/v1/internal/agent/runs/" + dispatched.runId())
                        .header("X-Agent-Service-Token", "not-the-token")
                        .header("X-Agent-Capability", dispatched.capability()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theServiceTokenAloneIsNotACapability() throws Exception {
        Dispatched dispatched = dispatchedRun("Message with a dispatched run");

        mockMvc.perform(get("/api/v1/internal/agent/runs/" + dispatched.runId())
                        .header("X-Agent-Service-Token", INBOUND_TOKEN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aForgedCapabilityIsRejected() throws Exception {
        Dispatched dispatched = dispatchedRun("Message with a dispatched run");

        mockMvc.perform(get("/api/v1/internal/agent/runs/" + dispatched.runId())
                        .header("X-Agent-Service-Token", INBOUND_TOKEN)
                        .header("X-Agent-Capability", "forged-capability-value"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aValidPairCanReadTheRunContext() throws Exception {
        Dispatched dispatched = dispatchedRun("Message with a dispatched run");

        mockMvc.perform(get("/api/v1/internal/agent/runs/" + dispatched.runId())
                        .header("X-Agent-Service-Token", INBOUND_TOKEN)
                        .header("X-Agent-Capability", dispatched.capability()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value(dispatched.runId()))
                .andExpect(jsonPath("$.data.patientId").value(100))
                .andExpect(jsonPath("$.data.state").value("RUNNING"));
    }

    @Test
    void theServiceTokenAloneCannotReadPatientRecords() throws Exception {
        // The internal allowlist is the two /internal/agent operations; the patient API
        // still needs a patient JWT, so the service credential buys nothing there.
        mockMvc.perform(get("/api/v1/patients/100")
                        .header("X-Agent-Service-Token", INBOUND_TOKEN))
                .andExpect(status().isUnauthorized());
    }

    // ── capability scope ────────────────────────────────────────────

    @Test
    void aCapabilityCannotBeUsedForAnotherRun() throws Exception {
        Dispatched first = dispatchedRun("First run");
        Dispatched second = dispatchedRun("Second run");

        mockMvc.perform(get("/api/v1/internal/agent/runs/" + second.runId())
                        .header("X-Agent-Service-Token", INBOUND_TOKEN)
                        .header("X-Agent-Capability", first.capability()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anExpiredCapabilityIsRejected() throws Exception {
        Dispatched dispatched = dispatchedRun("Message whose capability will expire");
        jdbcTemplate.update("UPDATE agent_capability SET expires_at = ? WHERE run_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)), dispatched.runId());

        mockMvc.perform(get("/api/v1/internal/agent/runs/" + dispatched.runId())
                        .header("X-Agent-Service-Token", INBOUND_TOKEN)
                        .header("X-Agent-Capability", dispatched.capability()))
                .andExpect(status().isForbidden());
    }

    @Test
    void aCapabilityFromAPreviousGenerationIsRejected() throws Exception {
        Dispatched dispatched = dispatchedRun("Message that will be re-dispatched");
        // What a re-claim does: the generation moves on, so the old worker is fenced.
        jdbcTemplate.update("UPDATE agent_run SET execution_generation = execution_generation + 1 "
                + "WHERE id = ?", dispatched.runId());

        mockMvc.perform(get("/api/v1/internal/agent/runs/" + dispatched.runId())
                        .header("X-Agent-Service-Token", INBOUND_TOKEN)
                        .header("X-Agent-Capability", dispatched.capability()))
                .andExpect(status().isForbidden());
    }

    @Test
    void aSuccessfulResultMovesTheRunAndRevokesTheCapability() throws Exception {
        Dispatched dispatched = dispatchedRun("Message whose result arrives");

        mockMvc.perform(post("/api/v1/internal/agent/runs/" + dispatched.runId() + "/results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "generation", dispatched.generation(), "outcome", "DRAFT_READY")))
                        .header("X-Agent-Service-Token", INBOUND_TOKEN)
                        .header("X-Agent-Capability", dispatched.capability()))
                .andExpect(status().isOk());

        assertEquals("WAITING_REVIEW", runState(dispatched.runId()));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_capability WHERE run_id = ?", Integer.class, dispatched.runId()),
                "the capability is gone once the run stops accepting work");

        mockMvc.perform(get("/api/v1/internal/agent/runs/" + dispatched.runId())
                        .header("X-Agent-Service-Token", INBOUND_TOKEN)
                        .header("X-Agent-Capability", dispatched.capability()))
                .andExpect(status().isForbidden());
    }

    @Test
    void aResultForAnotherGenerationIsAConflict() throws Exception {
        Dispatched dispatched = dispatchedRun("Message whose result is stale");

        mockMvc.perform(post("/api/v1/internal/agent/runs/" + dispatched.runId() + "/results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "generation", dispatched.generation() + 1, "outcome", "DRAFT_READY")))
                        .header("X-Agent-Service-Token", INBOUND_TOKEN)
                        .header("X-Agent-Capability", dispatched.capability()))
                .andExpect(status().isConflict());

        assertEquals("RUNNING", runState(dispatched.runId()), "the run is untouched");
    }

    @Test
    void aRevokedPatientAccountStopsTheWorker() throws Exception {
        Dispatched dispatched = dispatchedRun("Message whose patient is later disabled");
        jdbcTemplate.update("UPDATE patient_auth SET status = 0 WHERE patient_id = 100");
        try {
            mockMvc.perform(get("/api/v1/internal/agent/runs/" + dispatched.runId())
                            .header("X-Agent-Service-Token", INBOUND_TOKEN)
                            .header("X-Agent-Capability", dispatched.capability()))
                    .andExpect(status().isForbidden());
        } finally {
            jdbcTemplate.update("UPDATE patient_auth SET status = 1 WHERE patient_id = 100");
        }
    }

    @Test
    void onlyTheDigestIsStored() throws Exception {
        Dispatched dispatched = dispatchedRun("Message whose capability must not be stored");

        String digest = jdbcTemplate.queryForObject(
                "SELECT digest FROM agent_capability WHERE run_id = ?", String.class, dispatched.runId());
        assertTrue(digest.matches("[0-9a-f]{64}"), "a SHA-256 digest, not the token: " + digest);
        assertNotEquals(dispatched.capability(), digest);
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_run WHERE last_error LIKE ?", Integer.class,
                "%" + dispatched.capability() + "%"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_capability WHERE digest = ?", Integer.class,
                dispatched.capability()));
    }

    // ── direction and time ──────────────────────────────────────────

    @Test
    void theDispatchCarriesTheOutboundTokenAndNotTheInboundOne() throws Exception {
        dispatchedRun("Message whose dispatch credential is inspected");

        assertEquals(1, AUTHORIZATIONS.size());
        assertEquals("Bearer " + OUTBOUND_TOKEN, AUTHORIZATIONS.get(0),
                "Java must present its outbound credential to the agent service");
        assertNotEquals("Bearer " + INBOUND_TOKEN, AUTHORIZATIONS.get(0));
    }

    @Test
    void theOutboundTokenIsNotAnInboundCredential() throws Exception {
        // Directional credentials: the value Java sends out cannot be replayed back in with
        // a valid capability.
        Dispatched dispatched = dispatchedRun("Message whose outbound token is replayed");

        mockMvc.perform(get("/api/v1/internal/agent/runs/" + dispatched.runId())
                        .header("X-Agent-Service-Token", OUTBOUND_TOKEN)
                        .header("X-Agent-Capability", dispatched.capability()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aLeaseThatExpiredRefusesTheWorker() throws Exception {
        Dispatched dispatched = dispatchedRun("Message whose lease expires before its result");
        jdbcTemplate.update("UPDATE agent_run SET lease_expires_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)), dispatched.runId());

        mockMvc.perform(get("/api/v1/internal/agent/runs/" + dispatched.runId())
                        .header("X-Agent-Service-Token", INBOUND_TOKEN)
                        .header("X-Agent-Capability", dispatched.capability()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/internal/agent/runs/" + dispatched.runId() + "/results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "generation", dispatched.generation(), "outcome", "DRAFT_READY")))
                        .header("X-Agent-Service-Token", INBOUND_TOKEN)
                        .header("X-Agent-Capability", dispatched.capability()))
                .andExpect(status().isForbidden());
        assertEquals("RUNNING", runState(dispatched.runId()),
                "no reconcile ran, so only the authorization check refuses this worker");
    }

    @Test
    void aRunPastItsDeadlineRefusesTheWorker() throws Exception {
        Dispatched dispatched = dispatchedRun("Message whose deadline passes before its result");
        jdbcTemplate.update("UPDATE agent_run SET deadline_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)), dispatched.runId());

        mockMvc.perform(post("/api/v1/internal/agent/runs/" + dispatched.runId() + "/results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "generation", dispatched.generation(), "outcome", "DRAFT_READY")))
                        .header("X-Agent-Service-Token", INBOUND_TOKEN)
                        .header("X-Agent-Capability", dispatched.capability()))
                .andExpect(status().isForbidden());
        assertEquals("RUNNING", runState(dispatched.runId()));
    }

    // ── helpers ─────────────────────────────────────────────────────

    private Dispatched dispatchedRun(String content) throws Exception {
        CAPABILITIES.clear();
        AUTHORIZATIONS.clear();
        MvcResult result = mockMvc.perform(post("/api/v1/patient/me/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverId", 2, "content", content)))
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andReturn();
        long messageId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        Map<String, Object> run = jdbcTemplate.queryForMap(
                "SELECT id, execution_generation, state FROM agent_run WHERE message_id = ?", messageId);
        long runId = ((Number) run.get("id")).longValue();
        assertEquals("PENDING", run.get("state"));
        agentRuns().dispatchDue();

        assertEquals(1, CAPABILITIES.size(), "the dispatch must present a capability");
        assertFalse(CAPABILITIES.get(0).isBlank(), "the capability header must not be empty");
        return new Dispatched(runId, CAPABILITIES.get(0), generationOf(runId));
    }

    private long generationOf(long runId) {
        return jdbcTemplate.queryForObject(
                "SELECT execution_generation FROM agent_run WHERE id = ?", Long.class, runId);
    }

    private String runState(long runId) {
        return jdbcTemplate.queryForObject(
                "SELECT state FROM agent_run WHERE id = ?", String.class, runId);
    }

    private com.example.medical.module.chat.service.AgentRunService agentRuns() {
        return applicationContext.getBean(com.example.medical.module.chat.service.AgentRunService.class);
    }

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    private record Dispatched(long runId, String capability, long generation) {}
}
