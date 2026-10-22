package com.example.medical;

import com.example.medical.module.chat.agent.AgentDispatchProperties;
import com.example.medical.module.chat.service.AgentRunService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round 55.4 — the real two-process path, on H2 + SQLite.
 * <p>
 * The Java application runs on a random port; the Python service is a real uvicorn process
 * started from this test, pointed at that port. The first dispatch is bound to fail on
 * purpose — the Python process is given a wrong outbound credential, so its result
 * submission is refused (401) and the dispatch fails with 500 — which is what makes Java
 * requeue the run and claim it again under a higher generation. A second Python process,
 * with the correct credential but the same SQLite checkpoint, then resumes the run: the
 * result it submits carries the new generation, and Java ends in WAITING_REVIEW.
 * <p>
 * What this proves: the two processes talk to each other with the right credentials, the
 * retry really increments the generation, the checkpoint survives the Python process that
 * wrote it, and a message is stored and readable while the agent is failing. What it does
 * not prove is the absence of recomputation inside the resumed graph — that is covered by
 * the worker tests, which count provider calls. Postgres is not exercised here by decision
 * of 2026-10-22 (see the roadmap): H2 + SQLite is the current environment.
 */
@EnabledIfSystemProperty(named = "agent.e2e", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:agent_e2e;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.h2.console.enabled=false",
        "app.rate-limit.enabled=false",
        "app.agent.enabled=true",
        // The test drives the dispatcher; a background pass would race it.
        "app.agent.scheduling-enabled=false",
        "app.agent.inbound-token=java-inbound-token",
        "app.agent.outbound-token=java-outbound-token",
        "app.agent.lease=120s",
        "app.agent.deadline=30m",
        "app.agent.max-attempts=3",
        "app.agent.base-backoff=1s",
        "app.agent.capability-ttl=5m",
        "app.agent.request-timeout=10s",
        "app.agent.poll-interval-ms=3600000",
        // Replaced at runtime with the port the Python process actually listens on.
        "app.agent.base-url=http://127.0.0.1:1",
})
@Sql(scripts = "/cleanup-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class AgentEndToEndIntegrationTest {

    private static final String PATIENT_TOKEN_ENV = "java-inbound-token";

    @LocalServerPort
    private int javaPort;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AgentRunService agentRuns;

    @Autowired
    private AgentDispatchProperties agentProperties;

    private Path workspace;
    private Path pythonLog;
    private Process python;

    @BeforeAll
    void setUpWorkspace() throws IOException {
        workspace = Files.createTempDirectory("agent-e2e");
    }

    @AfterAll
    void cleanUp() throws IOException {
        stopPython();
        try (var files = Files.walk(workspace)) {
            files.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void aFailedCallbackIsRetriedWithANewGenerationAndEndsInWaitingReview() throws Exception {
        String patientToken = patientLogin();
        Path checkpoints = workspace.resolve("checkpoints.sqlite");

        // The agent's first process cannot submit results: the credential it presents is
        // wrong, so Java refuses the callback and the dispatch fails.
        String firstUrl = startPython("wrong-outbound-token", checkpoints);
        agentProperties.setBaseUrl(firstUrl);

        long messageId = sendPatientMessage(patientToken, "Can I reschedule my appointment?");
        assertEquals("PENDING", runState(messageId));

        assertEquals(1, agentRuns.dispatchDue());

        Map<String, Object> failed = runFor(messageId);
        assertEquals("PENDING", failed.get("state"), "a refused callback goes back to the queue");
        assertEquals(1, ((Number) failed.get("attempt")).intValue());
        assertEquals(1L, ((Number) failed.get("execution_generation")).longValue());
        assertEquals("AGENT_HTTP_500", failed.get("last_error"),
                "the callback was refused, so the dispatch failed as an HTTP error");

        // Human messaging is unaffected: the message is stored and readable while the
        // agent has just failed.
        JsonNode conversation = get("/api/v1/patient/me/messages/2", patientToken);
        assertEquals(200, conversation.get("code").asInt());
        assertTrue(conversation.get("data").get("records").toString()
                .contains("reschedule my appointment"));

        // A second Python process with the correct credential and the same checkpoint: it
        // resumes the run and submits the result for the generation Java is waiting for.
        stopPython();
        String secondUrl = startPython(PATIENT_TOKEN_ENV, checkpoints);
        agentProperties.setBaseUrl(secondUrl);
        jdbc.update("UPDATE agent_run SET next_attempt_at = ? WHERE message_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)), messageId);

        assertEquals(1, agentRuns.dispatchDue());

        Map<String, Object> finished = runFor(messageId);
        assertEquals("WAITING_REVIEW", finished.get("state"));
        assertEquals(2, ((Number) finished.get("attempt")).intValue());
        assertEquals(2L, ((Number) finished.get("execution_generation")).longValue(),
                "Java's retry claimed the run under a new generation");
        assertEquals(100L, ((Number) finished.get("patient_id")).longValue());
        assertNull(finished.get("last_error"), "the successful attempt clears the error");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_run WHERE message_id = ?", Integer.class, messageId),
                "still one job for one message");
    }

    // ── the Python process ──────────────────────────────────────────

    private String startPython(String outboundToken, Path checkpoints) throws Exception {
        int port = freePort();
        pythonLog = workspace.resolve("python-" + port + ".log");
        ProcessBuilder builder = new ProcessBuilder(
                pythonExecutable().toString(), "-m", "uvicorn", "medical_agent.api.main:app",
                "--port", String.valueOf(port), "--log-level", "warning")
                .directory(agentDirectory().toFile())
                .redirectErrorStream(true)
                .redirectOutput(pythonLog.toFile());
        Map<String, String> env = builder.environment();
        env.put("PYTHONPATH", "src");
        env.put("MEDICAL_AGENT_INBOUND_TOKEN", "java-outbound-token");
        env.put("MEDICAL_AGENT_OUTBOUND_TOKEN", outboundToken);
        env.put("MEDICAL_AGENT_JAVA_BASE_URL", "http://127.0.0.1:" + javaPort);
        env.put("MEDICAL_AGENT_CHECKPOINT_URL", "sqlite://" + checkpoints.toAbsolutePath());
        env.put("MEDICAL_AGENT_PROVIDER", "mock");
        env.put("MEDICAL_AGENT_REQUEST_TIMEOUT_SECONDS", "5");
        python = builder.start();
        String baseUrl = "http://127.0.0.1:" + port;
        awaitReady(baseUrl);
        return baseUrl;
    }

    private void awaitReady(String baseUrl) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1)).build();
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadline) {
            if (!python.isAlive()) {
                fail("the Python service exited: " + pythonLogText());
            }
            try {
                HttpResponse<String> response = client.send(
                        HttpRequest.newBuilder(URI.create(baseUrl + "/health/ready")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) return;
            } catch (IOException ignored) {
                // not listening yet
            }
            Thread.sleep(200);
        }
        fail("the Python service did not become ready: " + pythonLogText());
    }

    private void stopPython() {
        if (python == null) return;
        python.destroy();
        try {
            if (!python.waitFor(15, TimeUnit.SECONDS)) python.destroyForcibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            python.destroyForcibly();
        }
        python = null;
    }

    private String pythonLogText() {
        try {
            return Files.readString(pythonLog, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "(no python log)";
        }
    }

    private Path agentDirectory() {
        Path candidate = Path.of("..", "medical-agent");
        assertTrue(Files.isDirectory(candidate), "medical-agent/ must sit next to medical-server/");
        return candidate;
    }

    private Path pythonExecutable() {
        Path venv = agentDirectory().resolve(".venv/bin/python");
        return Files.isExecutable(venv) ? venv : Path.of("python3");
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    // ── HTTP helpers ────────────────────────────────────────────────

    private String patientLogin() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("username", "patient1", "password", "patient123"));
        JsonNode node = post("/api/v1/patient/login", body, null);
        assertEquals(200, node.get("code").asInt());
        return node.get("data").get("token").asText();
    }

    private long sendPatientMessage(String token, String content) throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("receiverId", 2, "content", content));
        JsonNode node = post("/api/v1/patient/me/messages", body, token);
        assertEquals(200, node.get("code").asInt());
        return node.get("data").get("id").asLong();
    }

    private JsonNode post(String path, String body, String token) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        ResponseEntity<String> response = rest.exchange(baseUrl() + path, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        return objectMapper.readTree(response.getBody());
    }

    private JsonNode get(String path, String token) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> response = rest.exchange(baseUrl() + path, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        return objectMapper.readTree(response.getBody());
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + javaPort;
    }

    private String runState(long messageId) {
        return jdbc.queryForObject("SELECT state FROM agent_run WHERE message_id = ?",
                String.class, messageId);
    }

    private Map<String, Object> runFor(long messageId) {
        return jdbc.queryForMap("SELECT id, patient_id, state, attempt, execution_generation, "
                + "last_error, next_attempt_at FROM agent_run WHERE message_id = ?", messageId);
    }
}
