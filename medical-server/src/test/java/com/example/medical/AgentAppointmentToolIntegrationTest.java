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
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Round 58.1 — the agent may read the appointments of the patient it serves, and nothing else.
 * <p>
 * The patient is derived from the run, the query is bounded to the future, and the payload carries
 * identity, status, timing and display fields only: no chief complaint, notes, description or codes.
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
class AgentAppointmentToolIntegrationTest extends IntegrationTestSupport {

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
    private long runId;
    private String capability;

    @BeforeAll
    void authenticate() throws Exception {
        patientToken = patientLogin("patient1", "patient123");
    }

    @AfterAll
    void stop() {
        AGENT_STUB.stop(0);
    }

    @BeforeEach
    void reset() throws Exception {
        CAPABILITIES.clear();
        jdbc.update("DELETE FROM agent_review_event");
        jdbc.update("DELETE FROM agent_review_decision");
        jdbc.update("DELETE FROM agent_review_claim");
        jdbc.update("DELETE FROM agent_run_result");
        jdbc.update("DELETE FROM agent_run");
        jdbc.update("DELETE FROM message");
        jdbc.update("DELETE FROM chat_conversation");
        // Only this test's rows: the seeded appointments belong to other test classes.
        jdbc.update("DELETE FROM appointment WHERE id IN (900, 901, 902) OR id >= 1000");
        jdbc.update("UPDATE patient_auth SET status = 1 WHERE patient_id = 100");

        MvcResult sent = mockMvc.perform(post("/api/v1/patient/me/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("receiverId", 2, "content", "When is my next visit?")))
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andReturn();
        long messageId = objectMapper.readTree(sent.getResponse().getContentAsString())
                .get("data").get("id").asLong();
        runs.dispatchDue();
        runId = jdbc.queryForObject(
                "SELECT id FROM agent_run WHERE message_id = ?", Long.class, messageId);
        capability = CAPABILITIES.get(CAPABILITIES.size() - 1);

        // One future appointment for patient 100 and one for another patient.
        insertAppointment(900, 100, 2, LocalDateTime.now().plusDays(3), "Cardiology");
        insertAppointment(901, 101, 2, LocalDateTime.now().plusDays(3), "Dermatology");
    }

    @Test
    void onlyTheRunsOwnAppointmentsAreVisible() throws Exception {
        mockMvc.perform(appointments())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].appointmentId").value(900))
                .andExpect(jsonPath("$.data[0].timezone")
                        .value(java.time.ZoneId.systemDefault().getId()));
    }

    @Test
    void anotherPatientsAppointmentDoesNotExistForThisRun() throws Exception {
        mockMvc.perform(appointment(901))
                .andExpect(status().isNotFound());
        mockMvc.perform(appointment(900))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appointmentId").value(900));
    }

    @Test
    void thePayloadCarriesNoClinicalText() throws Exception {
        String body = mockMvc.perform(appointment(900))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrueAbsent(body, "chiefComplaint");
        assertTrueAbsent(body, "notes");
        assertTrueAbsent(body, "description");
        assertTrueAbsent(body, "icd10");
        assertTrueAbsent(body, "cptCode");
        org.junit.jupiter.api.Assertions.assertTrue(
                body.contains("Cardiology"), "the display fields are present");
    }

    @Test
    void theQueryIsBounded() throws Exception {
        // 21 live appointments inside the window, one at day 366 and one in the past.
        for (int index = 0; index < 21; index++) {
            insertAppointment(1000 + index, 100, 2,
                    LocalDateTime.now().plusDays(1).plusHours(index), "Live");
        }
        insertAppointment(1100, 100, 2, LocalDateTime.now().plusDays(366), "TooFar");
        insertAppointment(902, 100, 2, LocalDateTime.now().minusDays(2), "Past");

        mockMvc.perform(get("/api/v1/internal/agent/runs/" + runId + "/appointments")
                        .param("limit", "500")
                        .header("X-Agent-Service-Token", "test-inbound-token")
                        .header("X-Agent-Capability", capability))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(20));

        mockMvc.perform(get("/api/v1/internal/agent/runs/" + runId + "/appointments")
                        .param("limit", "3")
                        .header("X-Agent-Service-Token", "test-inbound-token")
                        .header("X-Agent-Capability", capability))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    void theWindowKeepsPastAndTooDistantAppointmentsOut() throws Exception {
        insertAppointment(1500, 100, 2, LocalDateTime.now().minusDays(1), "PastOnly");
        insertAppointment(1501, 100, 2, LocalDateTime.now().plusDays(10), "Inside");
        insertAppointment(1502, 100, 2, LocalDateTime.now().plusDays(366), "TooFarOnly");

        mockMvc.perform(appointments())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.appointmentId == 1501)]").exists())
                .andExpect(jsonPath("$.data[?(@.appointmentId == 1500)]").doesNotExist())
                .andExpect(jsonPath("$.data[?(@.appointmentId == 1502)]").doesNotExist());
    }

    @Test
    void cancelledAppointmentsDoNotFillTheList() throws Exception {
        for (int index = 0; index < 20; index++) {
            insertAppointment(1200 + index, 100, 2,
                    LocalDateTime.now().plusHours(1).plusMinutes(index), "Cancelled", 2);
        }
        insertAppointment(1300, 100, 2, LocalDateTime.now().plusDays(5), "Live");

        mockMvc.perform(appointments())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].department").value("Live"));

        // The ID lookup still reports the patient's own cancelled appointment.
        mockMvc.perform(appointment(1200))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(2));
    }

    @Test
    void aDeactivatedAccountCannotReadAppointments() throws Exception {
        try {
            jdbc.update("UPDATE patient_auth SET status = 0 WHERE patient_id = 100");

            mockMvc.perform(appointments()).andExpect(status().isForbidden());
            mockMvc.perform(appointment(900)).andExpect(status().isForbidden());
        } finally {
            // The database is shared with the other test classes: leaving the account disabled
            // would break their logins when this test runs last (Round 58.1 review).
            jdbc.update("UPDATE patient_auth SET status = 1 WHERE patient_id = 100");
        }
    }

    @Test
    void theReturnedZoneMatchesTheStoredWallTime() throws Exception {
        java.util.TimeZone original = java.util.TimeZone.getDefault();
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"));
        try {
            LocalDateTime stored = LocalDateTime.now().plusHours(1).withNano(0);
            insertAppointment(1400, 100, 2, stored, "Soon");

            mockMvc.perform(appointments())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[?(@.department == 'Soon')]").exists())
                    // The digits are the stored wall time and the label is the zone they belong to,
                    // so a consumer reads back the same instant the write path meant.
                    .andExpect(jsonPath("$.data[?(@.department == 'Soon')].timezone")
                            .value("America/Los_Angeles"));
        } finally {
            java.util.TimeZone.setDefault(original);
        }
    }

    // ── helpers ─────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.RequestBuilder appointments() {
        return get("/api/v1/internal/agent/runs/" + runId + "/appointments")
                .header("X-Agent-Service-Token", "test-inbound-token")
                .header("X-Agent-Capability", capability);
    }

    private org.springframework.test.web.servlet.RequestBuilder appointment(long id) {
        return get("/api/v1/internal/agent/runs/" + runId + "/appointments/" + id)
                .header("X-Agent-Service-Token", "test-inbound-token")
                .header("X-Agent-Capability", capability);
    }

    private static void assertTrueAbsent(String body, String field) {
        org.junit.jupiter.api.Assertions.assertFalse(body.contains(field),
                "the payload must not carry " + field + ": " + body);
    }

    private void insertAppointment(long id, long patientId, long doctorId, LocalDateTime when,
                                   String department) {
        insertAppointment(id, patientId, doctorId, when, department, 1);
    }

    private void insertAppointment(long id, long patientId, long doctorId, LocalDateTime when,
                                   String department, int status) {
        jdbc.update("INSERT INTO appointment (id, patient_id, doctor_id, appointment_time, status, "
                        + "visit_type, department, duration, create_time, update_time) "
                        + "VALUES (?,?,?,?,?,'FOLLOW_UP',?,30,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                id, patientId, doctorId, Timestamp.valueOf(when), status, department);
    }
}
