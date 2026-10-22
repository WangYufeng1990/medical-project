package com.example.medical;

import com.example.medical.module.chat.entity.Message;
import com.example.medical.module.chat.repository.MessageRepository;
import com.example.medical.module.chat.service.AgentRunService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round 55.2 — a queued job survives the application context that queued it.
 * <p>
 * The dispatcher's other tests run inside one Spring context, so rebuilding the application
 * is the only way to show what the durable row is for. Both contexts run in the <b>same
 * JVM</b> and share one in-memory database kept alive by {@code DB_CLOSE_DELAY=-1}: the
 * first leaves a PENDING job behind and the second dispatches it.
 * <p>
 * This proves context rebuild, not crash recovery. A JVM exit, a crashed process or a
 * disk-backed restore is a different question (the in-memory database would be gone), and
 * this test does not answer it.
 */
class AgentRestartRecoveryIntegrationTest {

    private static final String DATABASE =
            "jdbc:h2:mem:agent_restart_probe;MODE=MySQL;DB_CLOSE_DELAY=-1";
    private static final HttpServer AGENT_STUB;
    private static final String BASE_URL;
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static final List<String> BODIES = new CopyOnWriteArrayList<>();

    static {
        try {
            AGENT_STUB = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            AGENT_STUB.createContext("/internal/v1/runs", exchange -> {
                BODIES.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                CALLS.incrementAndGet();
                exchange.sendResponseHeaders(202, -1);
                exchange.close();
            });
            AGENT_STUB.start();
            BASE_URL = "http://127.0.0.1:" + AGENT_STUB.getAddress().getPort();
        } catch (IOException e) {
            throw new IllegalStateException("could not start the agent stub", e);
        }
    }

    @AfterAll
    static void stopStub() {
        AGENT_STUB.stop(0);
    }

    @Test
    void aJobQueuedBeforeARestartIsDispatchedByTheNextContext() throws Exception {
        long messageId;
        long runId;

        try (ConfigurableApplicationContext first = startApplication()) {
            Message message = new Message();
            message.setSenderId(100L);
            message.setSenderType("PATIENT");
            message.setReceiverId(2L);
            message.setReceiverType("STAFF");
            message.setContent("A message queued before the restart");
            message.setIsRead(0);
            message.setCreateTime(LocalDateTime.now());
            message.setUpdateTime(LocalDateTime.now());
            messageId = first.getBean(MessageRepository.class).save(message).getId();

            LocalDateTime now = LocalDateTime.now();
            JdbcTemplate jdbc = first.getBean(JdbcTemplate.class);
            jdbc.update("INSERT INTO agent_run (message_id, patient_id, conversation_revision, state, "
                            + "attempt, max_attempts, next_attempt_at, deadline_at, execution_generation, "
                            + "create_time, update_time, is_deleted, version) "
                            + "VALUES (?,?,?, 'PENDING', 0, 3, ?, ?, 0, ?, ?, 0, 0)",
                    messageId, 100L, 1L, Timestamp.valueOf(now), Timestamp.valueOf(now.plusMinutes(30)),
                    Timestamp.valueOf(now), Timestamp.valueOf(now));
            runId = jdbc.queryForObject("SELECT id FROM agent_run WHERE message_id = ?",
                    Long.class, messageId);
            assertEquals("PENDING", state(jdbc, messageId), "the job is durable before the restart");
        }

        // A new application instance on the same database.
        try (ConfigurableApplicationContext second = startApplication()) {
            JdbcTemplate jdbc = second.getBean(JdbcTemplate.class);
            assertEquals("PENDING", state(jdbc, messageId), "the restart must not consume the job");

            int dispatched = second.getBean(AgentRunService.class).dispatchDue();

            assertEquals(1, dispatched, "the new process picks the queued job up");
        }

        // Read through a fresh connection: both contexts are closed, the database is not.
        JdbcTemplate probe = new JdbcTemplate(
                new org.springframework.jdbc.datasource.DriverManagerDataSource(DATABASE, "sa", ""));
        Map<String, Object> run = probe.queryForMap(
                "SELECT state, attempt FROM agent_run WHERE message_id = ?", messageId);
        assertEquals("RUNNING", run.get("state"));
        assertEquals(1, ((Number) run.get("attempt")).intValue());
        assertEquals(1, CALLS.get(), "one dispatch for one job");
        assertTrue(BODIES.get(0).contains("\"run_id\":" + runId),
                "the restarted process sends the same run id: " + BODIES.get(0));
        assertTrue(BODIES.get(0).contains("A message queued before the restart"));
    }

    private ConfigurableApplicationContext startApplication() {
        return new SpringApplicationBuilder(MedicalApplication.class)
                .profiles("h2")
                .run("--spring.datasource.url=" + DATABASE,
                        "--spring.h2.console.enabled=false",
                        "--app.rate-limit.enabled=false",
                        "--app.agent.enabled=true",
                        "--app.agent.scheduling-enabled=false",
                        "--app.agent.base-url=" + BASE_URL,
                        "--app.agent.outbound-token=test-outbound-token",
                        "--app.agent.inbound-token=test-inbound-token",
                        "--app.agent.request-timeout=5s",
                        "--server.port=0");
    }

    private String state(JdbcTemplate jdbc, long messageId) {
        return jdbc.queryForObject("SELECT state FROM agent_run WHERE message_id = ?",
                String.class, messageId);
    }
}
