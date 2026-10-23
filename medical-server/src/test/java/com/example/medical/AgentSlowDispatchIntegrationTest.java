package com.example.medical;

import com.example.medical.module.chat.agent.AgentDispatchProperties;
import com.example.medical.module.chat.service.AgentRunService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Round 56.1 review — a model answer slower than the old 5 s Java timeout.
 * <p>
 * The service now calls a real model, so a dispatch can take seconds. With the previous defaults
 * (5 s timeout, 60 s lease) a slow-but-successful answer would have been declared failed while the
 * model was still working. Here the stub answers after 6 s, inside the configured timeout and well
 * inside the lease: the dispatch succeeds, the run stays RUNNING, and the lease is still live.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:agent_slow;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.h2.console.enabled=false",
        "app.rate-limit.enabled=false",
        "app.agent.enabled=true",
        "app.agent.scheduling-enabled=false",
        "app.agent.inbound-token=test-inbound-token",
        "app.agent.outbound-token=test-outbound-token",
        "app.agent.request-timeout=15s",
        "app.agent.lease=60s",
        "app.agent.deadline=30m",
        "app.agent.poll-interval-ms=3600000",
})
@Sql(scripts = "/cleanup-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class AgentSlowDispatchIntegrationTest extends IntegrationTestSupport {

    private static final HttpServer AGENT_STUB;
    private static final String BASE_URL;

    static {
        try {
            AGENT_STUB = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            AGENT_STUB.createContext("/internal/v1/runs", exchange -> {
                exchange.getRequestBody().readAllBytes();
                try {
                    Thread.sleep(6_000); // slower than the old 5 s timeout
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
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
    private AgentRunService agentRuns;

    @Autowired
    private AgentDispatchProperties agentProperties;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mockMvc;

    private String patientToken;

    @BeforeAll
    void authenticate() throws Exception {
        patientToken = patientLogin("patient1", "patient123");
    }

    @AfterAll
    void stop() {
        AGENT_STUB.stop(0);
    }

    @Test
    void aModelAnswerSlowerThanTheOldDefaultStillSucceeds() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/patient/me/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("receiverId", 2, "content", "Can I move my visit?")))
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andReturn();
        long messageId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        long started = System.nanoTime();
        assertEquals(1, agentRuns.dispatchDue());
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        Map<String, Object> run = jdbc.queryForMap(
                "SELECT state, attempt, lease_expires_at FROM agent_run WHERE message_id = ?",
                messageId);
        assertEquals("RUNNING", run.get("state"), "the dispatch must not be failed by the timeout");
        assertEquals(1, ((Number) run.get("attempt")).intValue());
        assertTrue(elapsedMillis >= 6_000, "the stub really was slow: " + elapsedMillis + " ms");
        assertTrue(((java.sql.Timestamp) run.get("lease_expires_at")).toLocalDateTime()
                .isAfter(java.time.LocalDateTime.now()), "the lease outlives the slow call");

        agentRuns.reconcile();
        assertEquals("RUNNING", jdbc.queryForObject(
                "SELECT state FROM agent_run WHERE message_id = ?", String.class, messageId),
                "reconcile must not reclaim the job it just dispatched");
        assertTrue(agentProperties.getLease().compareTo(agentProperties.getRequestTimeout()) > 0);
    }
}
