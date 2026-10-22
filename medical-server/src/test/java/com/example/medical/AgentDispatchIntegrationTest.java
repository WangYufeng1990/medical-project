package com.example.medical;

import com.example.medical.module.chat.agent.AgentDispatchProperties;
import com.example.medical.module.chat.agent.AgentRunClient;
import com.example.medical.module.chat.agent.AgentRunScheduler;
import com.example.medical.module.chat.agent.HttpAgentRunClient;
import com.example.medical.module.chat.dto.agent.AgentRunStartRequest;
import com.example.medical.module.chat.entity.Message;
import com.example.medical.module.chat.repository.MessageRepository;
import com.example.medical.module.chat.service.AgentRunService;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Round 55.2 — the durable dispatcher.
 * <p>
 * The queue is a table, so the properties that matter are about what survives: a stored
 * patient message always has a job, a job is claimed by one dispatcher at a time, a failed
 * attempt comes back later instead of vanishing, a lease that ran out is reclaimable and a
 * job past its deadline ends. The agent service is a local HTTP stub, so the test observes
 * what Java actually sends (the v1 contract) and can make it fail on demand.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "app.agent.enabled=true",
        // The poller is driven by the tests themselves: a background pass would race them.
        "app.agent.scheduling-enabled=false",
        "app.agent.outbound-token=test-outbound-token",
        "app.agent.inbound-token=test-inbound-token",
        "app.agent.poll-interval-ms=3600000",
        // A short lease so "expired while the batch was busy" is reachable, and a longer
        // client timeout so the slow dispatch still succeeds rather than timing out.
        "app.agent.lease=1s",
        "app.agent.deadline=30m",
        "app.agent.max-attempts=3",
        "app.agent.base-backoff=1s",
        "app.agent.max-backoff=2s",
        "app.agent.request-timeout=5s",
})
class AgentDispatchIntegrationTest extends IntegrationTestSupport {

    private static final HttpServer AGENT_STUB;
    private static final String BASE_URL;
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static final List<String> BODIES = new CopyOnWriteArrayList<>();
    private static volatile int stubStatus = 202;
    private static volatile String stubBody = "";
    private static volatile long firstRequestDelayMillis = 0;
    /** Runs inside the first dispatch request, before the stub answers. */
    private static volatile java.util.function.Consumer<Dispatch> onFirstDispatch = dispatch -> { };
    private static volatile boolean abortFirstDispatch = false;

    static {
        try {
            AGENT_STUB = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            AGENT_STUB.createContext("/internal/v1/runs", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                BODIES.add(body);
                String capability = exchange.getRequestHeaders().getFirst("X-Agent-Capability");
                int call = CALLS.incrementAndGet();
                if (call == 1) {
                    if (firstRequestDelayMillis > 0) {
                        try {
                            Thread.sleep(firstRequestDelayMillis);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    onFirstDispatch.accept(new Dispatch(body, capability));
                }
                if (abortFirstDispatch && call == 1) {
                    // No response headers: the client sees the connection end.
                    exchange.close();
                    return;
                }
                byte[] response = stubBody.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(stubStatus, response.length == 0 ? -1 : response.length);
                if (response.length > 0) exchange.getResponseBody().write(response);
                exchange.close();
            });
            AGENT_STUB.setExecutor(Executors.newFixedThreadPool(2));
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

    @Autowired
    private AgentRunService agentRuns;

    @Autowired
    private AgentRunScheduler agentScheduler;

    @Autowired
    private com.example.medical.module.chat.controller.AgentInternalController agentInternal;

    @Autowired
    private MessageRepository messageRepository;

    private String patientToken;
    private String doctorToken;

    @BeforeAll
    void authenticate() throws Exception {
        patientToken = patientLogin("patient1", "patient123");
        doctorToken = login("doctor1", "doctor123");
    }

    @BeforeEach
    void resetState() {
        stubStatus = 202;
        stubBody = "";
        firstRequestDelayMillis = 0;
        onFirstDispatch = dispatch -> { };
        abortFirstDispatch = false;
        CALLS.set(0);
        BODIES.clear();
        // Each test owns its queue: a PENDING row from a previous test would be dispatched
        // alongside the new one and make every call count ambiguous.
        jdbcTemplate.update("DELETE FROM agent_run");
    }

    @AfterAll
    void stopStub() {
        AGENT_STUB.stop(0);
    }

    // ── intake ──────────────────────────────────────────────────────

    @Test
    void aStoredPatientMessageCreatesOneDurableRun() throws Exception {
        long messageId = sendAsPatient("Can I move my Thursday appointment?");

        Map<String, Object> run = runFor(messageId);
        assertEquals("PENDING", run.get("state"));
        assertEquals(100L, ((Number) run.get("patient_id")).longValue());
        assertEquals(0, ((Number) run.get("attempt")).intValue());
        assertEquals(3, ((Number) run.get("max_attempts")).intValue());
        assertTrue(((Number) run.get("conversation_revision")).longValue() > 0,
                "the run records the conversation revision it was created against");
    }

    @Test
    void staffMessagesDoNotCreateAgentRuns() throws Exception {
        long messageId = sendAsDoctor("Your results look fine.");

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_run WHERE message_id = ?", Integer.class, messageId);
        assertEquals(0, rows, "only a patient message is model work");
    }

    // ── dispatch ────────────────────────────────────────────────────

    @Test
    void dispatchPostsTheV1ContractAndMarksTheRunRunning() throws Exception {
        long messageId = sendAsPatient("What are your opening hours?");

        assertEquals(1, agentRuns.dispatchDue());

        assertEquals(1, CALLS.get());
        JsonNode body = objectMapper.readTree(BODIES.get(0));
        assertEquals("v1", body.get("contract_version").asText());
        assertEquals(runFor(messageId).get("id"), body.get("run_id").asLong());
        assertEquals(messageId, body.get("message_id").asLong());
        assertEquals(100, body.get("patient_id").asLong());
        assertEquals("PATIENT", body.get("sender_type").asText());
        assertEquals("What are your opening hours?", body.get("content").asText());
        assertTrue(body.hasNonNull("request_id"));

        Map<String, Object> run = runFor(messageId);
        assertEquals("RUNNING", run.get("state"));
        assertEquals(1, ((Number) run.get("attempt")).intValue());
        assertEquals(1L, ((Number) run.get("execution_generation")).longValue());
    }

    @Test
    void aFailingAgentServiceBacksOffWithoutLosingTheRunOrTheMessage() throws Exception {
        stubStatus = 503;
        long messageId = sendAsPatient("A message while the agent is down");

        assertEquals(1, agentRuns.dispatchDue());

        Map<String, Object> run = runFor(messageId);
        assertEquals("PENDING", run.get("state"), "a failed attempt goes back to the queue");
        assertEquals(1, ((Number) run.get("attempt")).intValue());
        assertNotNull(run.get("last_error"));
        assertTrue(((java.sql.Timestamp) run.get("next_attempt_at")).toLocalDateTime()
                        .isAfter(LocalDateTime.now()),
                "the retry is scheduled, not immediate");
        Message stored = messageRepository.findById(messageId).orElseThrow();
        assertEquals("A message while the agent is down", stored.getContent(),
                "the human message is stored regardless of the agent");
    }

    @Test
    void twoDispatchersRaceButOnlyOneSends() throws Exception {
        long messageId = sendAsPatient("A message two dispatchers will see");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return agentRuns.dispatchDue();
            });
            var second = pool.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return agentRuns.dispatchDue();
            });
            start.countDown();
            int total = first.get(30, TimeUnit.SECONDS) + second.get(30, TimeUnit.SECONDS);
            assertTrue(total <= 1, "the guarded claim must let one caller win, both reported " + total);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, CALLS.get(), "the service must be called once for one job");
        assertEquals(1L, ((Number) runFor(messageId).get("attempt")).longValue());
        assertEquals("RUNNING", runFor(messageId).get("state"));
    }

    @Test
    void aResultAcceptedDuringTheDispatchCallIsNotOverwritten() throws Exception {
        // The worker answers the dispatch request's own result call before the dispatch
        // request finishes: the row is WAITING_REVIEW with no capability and no lease by the
        // time the dispatcher returns.
        long messageId = sendAsPatient("A result that beats the dispatch response");
        long runId = runIdFor(messageId);
        onFirstDispatch = dispatch -> {
            long generation = jdbcTemplate.queryForObject(
                    "SELECT execution_generation FROM agent_run WHERE id = ?", Long.class, runId);
            agentInternal.result("test-inbound-token", dispatch.capability(), runId,
                    resultBody(generation));
        };

        agentRuns.dispatchDue();

        assertEquals("WAITING_REVIEW", runFor(messageId).get("state"),
                "the dispatch acknowledgement must not reopen a run whose result arrived");
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_capability WHERE run_id = ?", Integer.class, runId));
    }

    @Test
    void aFailedDispatchAfterAnAcceptedResultDoesNotRequeue() throws Exception {
        long messageId = sendAsPatient("A result that arrives before the dispatch breaks");
        long runId = runIdFor(messageId);
        onFirstDispatch = dispatch -> {
            long generation = jdbcTemplate.queryForObject(
                    "SELECT execution_generation FROM agent_run WHERE id = ?", Long.class, runId);
            agentInternal.result("test-inbound-token", dispatch.capability(), runId,
                    resultBody(generation));
        };
        abortFirstDispatch = true;
        ch.qos.logback.classic.Logger serviceLogger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(AgentRunService.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        ch.qos.logback.classic.Level previousLevel = serviceLogger.getLevel();
        serviceLogger.addAppender(appender);
        serviceLogger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        try {
            agentRuns.dispatchDue();
        } finally {
            serviceLogger.setLevel(previousLevel);
            serviceLogger.detachAppender(appender);
        }

        assertEquals("WAITING_REVIEW", runFor(messageId).get("state"),
                "a late failure report must not put an accepted job back in the queue");
        assertEquals(1, ((Number) runFor(messageId).get("attempt")).intValue());
        assertEquals(1, CALLS.get());
        assertTrue(appender.list.stream().anyMatch(event ->
                        event.getFormattedMessage().contains("attempt had already moved on")),
                "the failure path must have run and been refused, not skipped: " + appender.list);
    }

    // ── recovery ────────────────────────────────────────────────────

    @Test
    void aFailedRunIsReplayedAfterItsBackoff() throws Exception {
        stubStatus = 503;
        long messageId = sendAsPatient("Retry me after the restart");
        agentRuns.dispatchDue();
        assertEquals("PENDING", runFor(messageId).get("state"));

        // The next pass, once the backoff has elapsed and the service is up.
        stubStatus = 202;
        jdbcTemplate.update("UPDATE agent_run SET next_attempt_at = ? WHERE message_id = ?",
                java.sql.Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)), messageId);
        agentRuns.dispatchDue();

        Map<String, Object> run = runFor(messageId);
        assertEquals("RUNNING", run.get("state"));
        assertEquals(2, ((Number) run.get("attempt")).intValue());
        assertEquals(2, CALLS.get(), "the failed attempt and the replay, once each");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_run WHERE message_id = ?", Integer.class, messageId),
                "a replay reuses the same job");
    }

    @Test
    void anExpiredLeaseIsReclaimedAndDispatchedAgain() throws Exception {
        long messageId = sendAsPatient("The worker died holding this job");
        jdbcTemplate.update("UPDATE agent_run SET state = 'RUNNING', attempt = 1, "
                        + "lease_owner = 'dead-worker', lease_expires_at = ? WHERE message_id = ?",
                java.sql.Timestamp.valueOf(LocalDateTime.now().minusMinutes(5)), messageId);

        agentRuns.reconcile();

        assertEquals("PENDING", runFor(messageId).get("state"), "an expired lease returns the job");
        agentRuns.dispatchDue();
        assertEquals("RUNNING", runFor(messageId).get("state"));
        assertEquals(2, ((Number) runFor(messageId).get("attempt")).intValue());
        assertEquals(1, CALLS.get());
    }

    @Test
    void aRunPastItsDeadlineEndsInsteadOfRetryingForever() throws Exception {
        long messageId = sendAsPatient("This job outlived its deadline");
        jdbcTemplate.update("UPDATE agent_run SET deadline_at = ? WHERE message_id = ?",
                java.sql.Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)), messageId);

        agentRuns.reconcile();

        Map<String, Object> run = runFor(messageId);
        assertEquals("FAILED", run.get("state"));
        assertNotNull(run.get("last_error"));
        assertEquals(0, CALLS.get(), "a deadline is decided in the database, not by calling out");
    }

    @Test
    void exhaustingTheAttemptsEndsTheRunWithoutAnotherCall() throws Exception {
        long messageId = sendAsPatient("The last permitted attempt");
        jdbcTemplate.update("UPDATE agent_run SET attempt = 3 WHERE message_id = ?", messageId);

        agentScheduler.runOnce();

        assertEquals("FAILED", runFor(messageId).get("state"));
        assertEquals(0, CALLS.get(), "a job out of attempts must not reach the service again");
        assertEquals(3, ((Number) runFor(messageId).get("attempt")).intValue(),
                "and its attempt counter must not move");
    }

    @Test
    void aJobWhoseLeaseExpiredOnItsLastAttemptEndsInsteadOfWaiting() throws Exception {
        long messageId = sendAsPatient("The worker died on the last attempt");
        jdbcTemplate.update("UPDATE agent_run SET state = 'RUNNING', attempt = 3, "
                        + "lease_owner = 'dead-worker', lease_expires_at = ? WHERE message_id = ?",
                java.sql.Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)), messageId);

        agentRuns.reconcile();

        assertEquals("FAILED", runFor(messageId).get("state"),
                "no attempt is left, so the row must not return to the queue");
        assertEquals(0, CALLS.get());
    }

    @Test
    void aJobPastItsDeadlineIsNeverSent() throws Exception {
        long messageId = sendAsPatient("This job's deadline has already passed");
        jdbcTemplate.update("UPDATE agent_run SET deadline_at = ? WHERE message_id = ?",
                java.sql.Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)), messageId);

        agentScheduler.runOnce();

        assertEquals(0, CALLS.get(), "the real pass order reconciles before it dispatches");
        assertEquals(0, ((Number) runFor(messageId).get("attempt")).intValue());
        assertEquals("FAILED", runFor(messageId).get("state"));
    }

    @Test
    void aJobThatExpiresWhileTheBatchIsBusyIsNotSent() throws Exception {
        long first = sendAsPatient("The job that will hold the dispatcher");
        long second = sendAsPatient("The job that expires while waiting");
        jdbcTemplate.update("UPDATE agent_run SET deadline_at = ? WHERE message_id = ?",
                java.sql.Timestamp.valueOf(LocalDateTime.now().plusSeconds(2)), second);
        firstRequestDelayMillis = 2_500; // longer than the second job has left

        agentScheduler.runOnce();

        assertEquals(1, CALLS.get(), "only the first job may be sent");
        assertEquals("PENDING", runFor(second).get("state"),
                "the claim refuses a job whose deadline passed while the batch was busy");
        assertEquals(0, ((Number) runFor(second).get("attempt")).intValue());
        assertEquals(1, ((Number) runFor(first).get("attempt")).intValue(),
                "the first job was attempted once");
        assertEquals("RUNNING", runFor(first).get("state"));

        agentRuns.reconcile();
        assertEquals("FAILED", runFor(second).get("state"), "the deadline sweep ends it");
        assertEquals("PENDING", runFor(first).get("state"),
                "and a dispatch slower than its own lease is reclaimed — which is why the "
                        + "lease must exceed the client timeout");
    }

    @Test
    void eachClaimGetsALeaseFromItsOwnMoment() throws Exception {
        long first = sendAsPatient("The slow job at the head of the batch");
        long second = sendAsPatient("The quick job behind it");
        firstRequestDelayMillis = 2_500; // longer than the configured 2s lease

        agentScheduler.runOnce();

        assertEquals(2, CALLS.get());
        Integer liveLease = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_run WHERE message_id = ? "
                        + "AND lease_expires_at > CURRENT_TIMESTAMP", Integer.class, second);
        assertEquals(1, liveLease,
                "the second job's lease starts when it is claimed, not when the batch started");
        assertEquals("RUNNING", runFor(second).get("state"));

        agentRuns.reconcile();
        assertEquals("RUNNING", runFor(second).get("state"),
                "reconcile must not reclaim a job whose lease started after the batch");
        assertTrue(first > 0);
    }

    @Test
    void aProviderErrorBodyNeverReachesTheDatabaseOrTheLogs() throws Exception {
        // The stub answers like a validation failure that echoes the input.
        String marker = "SYNTHETIC-PATIENT-TEXT-7f3a";
        stubStatus = 422;
        stubBody = "{\"detail\":\"invalid content: " + marker + "\"}";
        long messageId = sendAsPatient("A message the agent rejects");
        ch.qos.logback.classic.Logger serviceLogger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(AgentRunService.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        ch.qos.logback.classic.Level previousLevel = serviceLogger.getLevel();
        serviceLogger.addAppender(appender);
        // The retry line is DEBUG; without raising the level the appender would see nothing
        // and "no marker in the logs" would pass for the wrong reason.
        serviceLogger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        try {
            agentRuns.dispatchDue();
        } finally {
            serviceLogger.setLevel(previousLevel);
            serviceLogger.detachAppender(appender);
        }

        String recordedError = (String) runFor(messageId).get("last_error");
        assertEquals("AGENT_HTTP_422", recordedError, "only the category is stored");
        assertFalse(recordedError.contains(marker));
        assertTrue(appender.list.stream().anyMatch(event ->
                        event.getLevel() == ch.qos.logback.classic.Level.DEBUG
                                && event.getFormattedMessage().contains("AGENT_HTTP_422")),
                "the retry must be logged with its category, so the check below is not vacuous");
        assertFalse(appender.list.stream().anyMatch(event ->
                        event.getFormattedMessage().contains(marker)
                                || (event.getThrowableProxy() != null && event.getThrowableProxy()
                                .getMessage().contains(marker))),
                "the response body must not be logged either");
        assertEquals("PENDING", runFor(messageId).get("state"), "and the job is retried later");
        assertTrue(CALLS.get() >= 1);
    }

    @Test
    void aRefusedConnectionIsAFailureTheQueueCanRetry() {
        // The dispatcher's error path is "the client threw"; this pins what a refused
        // connection actually does instead of leaving it to be discovered in production.
        AgentDispatchProperties properties = new AgentDispatchProperties();
        properties.setBaseUrl("http://127.0.0.1:1");
        properties.setOutboundToken("test-outbound-token");
        properties.setRequestTimeout(Duration.ofMillis(500));
        AgentRunClient client = new HttpAgentRunClient(properties);
        AgentRunStartRequest request = new AgentRunStartRequest("v1", 1L, 1L, 100L, 1L,
                "PATIENT", "hello", "en", "request-1");

        assertThrows(RuntimeException.class, () -> client.start(request, "capability"));
    }

    // ── helpers ─────────────────────────────────────────────────────

    private long sendAsPatient(String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/patient/me/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverId", 2, "content", content)))
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    private long sendAsDoctor(String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverId", 100, "receiverType", "PATIENT", "content", content)))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    private long runIdFor(long messageId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM agent_run WHERE message_id = ?", Long.class, messageId);
    }

    private com.example.medical.module.chat.dto.agent.AgentRunResultRequest resultBody(long generation) {
        com.example.medical.module.chat.dto.agent.AgentRunResultRequest body =
                new com.example.medical.module.chat.dto.agent.AgentRunResultRequest();
        body.setGeneration(generation);
        body.setOutcome("DRAFT_READY");
        return body;
    }

    /** What the stub saw: the request body and the capability it was given. */
    private record Dispatch(String body, String capability) { }

    private Map<String, Object> runFor(long messageId) {
        return jdbcTemplate.queryForMap(
                "SELECT id, message_id, patient_id, conversation_revision, state, attempt, "
                        + "max_attempts, next_attempt_at, deadline_at, lease_owner, lease_expires_at, "
                        + "execution_generation, last_error FROM agent_run WHERE message_id = ?",
                messageId);
    }
}
