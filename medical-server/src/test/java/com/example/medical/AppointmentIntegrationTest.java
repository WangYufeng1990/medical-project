package com.example.medical;

import com.example.medical.module.appointment.entity.AppointmentStatus;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Appointment CRUD, conflict detection and status filtering.
 * <p>
 * The tests here form a deliberate sequence (create → update → delete, or a claim
 * lifecycle), so this class keeps an explicit order. Ordering is class-local: the
 * class still runs on its own because cleanup-test-data.sql restores the seeded
 * state before it starts.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AppointmentIntegrationTest extends IntegrationTestSupport {

    private String adminToken = null;

    @BeforeAll
    void authenticate() throws Exception {
        adminToken = login("admin", "admin123");
    }

    // ──────────────────────────────────────────────────────

    @Test
    @Order(42)
    void appointmentPage_shouldReturnPaginated() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/appointments")
                        .param("page", "1").param("size", "10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").get("total").asInt() >= 5);
    }

    @Test
    @Order(43)
    void appointmentPage_withStatusFilter_shouldFilter() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/appointments")
                        .param("status", "0")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        // Seed has 2 appointments with status=0 (scheduled)
        assertEquals(2, node.get("data").get("total").asInt());
    }

    @Test
    @Order(44)
    void getAppointmentById_shouldReturnAppointment() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/appointments/200")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertNotNull(node.get("data").get("patientName").asText());
        assertNotNull(node.get("data").get("doctorName").asText());
    }

    @Test
    @Order(45)
    void createAppointment_shouldSucceed() throws Exception {
        String time = LocalDateTime.now().plusDays(30).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        String body = objectMapper.writeValueAsString(Map.of(
                "patientId", 100,
                "doctorId", 2,
                "appointmentTime", time,
                "visitType", "FOLLOW_UP",
                "chiefComplaint", "Test complaint",
                "department", "Family Medicine",
                "duration", 30,
                "cptCode", "99213",
                "description", "Test appointment",
                "status", 0
        ));
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(46)
    void createAppointment_conflicting_shouldReturn409() throws Exception {
        // Create a future appointment, then a second one for the same doctor in the
        // same 30-min window — the second must be rejected with 409.
        // +90 days: order 45 already books doctor 2 at +30 days.
        String time = LocalDateTime.now().plusDays(90).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", 100, "doctorId", 2, "appointmentTime", time,
                                "description", "First booking", "status", 0)))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", 101, "doctorId", 2,
                                "appointmentTime", LocalDateTime.parse(time)
                                        .plusMinutes(15)
                                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")),
                                "description", "Conflict test", "status", 0)))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(47)
    void updateAppointment_shouldSucceed() throws Exception {
        String time = LocalDateTime.now().plusDays(60).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        String body = objectMapper.writeValueAsString(Map.of(
                "patientId", 100,
                "doctorId", 2,
                "appointmentTime", time,
                "status", 1,
                "description", "Updated appointment"
        ));
        mockMvc.perform(put("/api/v1/appointments/205")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(48)
    void deleteAppointment_asAdmin_shouldSucceed() throws Exception {
        mockMvc.perform(delete("/api/v1/appointments/205")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    /**
     * The charge a completed visit produces is written through the billing module
     * (52.3b): the amount rule, the DRAFT status and the audit row belong to the
     * module that owns charges. Patient 101 is used because no other test charges
     * that patient, so the audit count below can only come from this visit.
     */
    @Test
    @Order(49)
    void completingAVisit_shouldCreateTheChargeThroughTheBillingService() throws Exception {
        String time = LocalDateTime.now().plusDays(45)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        String visit = objectMapper.writeValueAsString(Map.of(
                "patientId", 101, "doctorId", 2, "appointmentTime", time,
                "visitType", "FOLLOW_UP", "chiefComplaint", "R52.3b visit",
                "cptCode", "99213", "description", "52.3b probe", "status", 0));
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON).content(visit)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        JsonNode page = objectMapper.readTree(mockMvc.perform(get("/api/v1/appointments")
                        .param("size", "50").header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString());
        long appointmentId = -1;
        for (JsonNode row : page.get("data").get("records")) {
            if ("R52.3b visit".equals(row.get("chiefComplaint").asText())) {
                appointmentId = row.get("id").asLong();
            }
        }
        assertTrue(appointmentId > 0, "the probe visit should be listed");

        assertEquals(0, chargeCreateAudits(101).get("data").get("total").asInt(),
                "no charge/CREATE row for patient 101 exists before this test");

        mockMvc.perform(put("/api/v1/appointments/" + appointmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", 101, "doctorId", 2, "appointmentTime", time,
                                "visitType", "FOLLOW_UP", "chiefComplaint", "R52.3b visit",
                                "cptCode", "99213", "description", "52.3b probe",
                                "status", AppointmentStatus.COMPLETED.code())))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        JsonNode charges = objectMapper.readTree(mockMvc.perform(get("/api/v1/charges")
                        .param("patientId", "101").param("size", "50")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString());
        JsonNode charge = null;
        for (JsonNode row : charges.get("data").get("records")) {
            if (row.get("appointmentId") != null && row.get("appointmentId").asLong() == appointmentId) {
                charge = row;
            }
        }
        assertNotNull(charge, "completing the visit should create its charge");
        assertEquals("99213", charge.get("cptCodes").asText());
        assertEquals("DRAFT", charge.get("status").asText());
        assertEquals(90.0, charge.get("chargeAmount").asDouble(), "a 992xx visit bills 90");
        assertEquals("R52.3b visit", charge.get("icd10Codes").asText(),
                "the visit's chief complaint is still carried onto the charge");

        // Audit rows are written asynchronously — poll briefly rather than assert instantly.
        long deadline = System.currentTimeMillis() + 5000;
        int audits = 0;
        while (audits == 0 && System.currentTimeMillis() < deadline) {
            audits = chargeCreateAudits(101).get("data").get("total").asInt();
            if (audits == 0) Thread.sleep(100);
        }
        assertEquals(1, audits, "completing a visit must leave a charge/CREATE audit row");
    }

    private JsonNode chargeCreateAudits(long patientId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/v1/audit-logs")
                        .param("module", "charge").param("action", "CREATE")
                        .param("patientId", String.valueOf(patientId)).param("size", "50")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString());
    }

    // ──────────────────────────────────────────────────────
}
