package com.example.medical;

import com.example.medical.module.appointment.entity.AppointmentStatus;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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

    private String adminToken, doctorToken = null;

    @BeforeAll
    void authenticate() throws Exception {
        adminToken = login("admin", "admin123");
        doctorToken = login("doctor1", "doctor123");
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
                "visitType", "FOLLOW_UP", "chiefComplaint", "synthetic headache",
                "icd10Codes", "R51.9", "cptCode", "99213", "description", "52.3b probe", "status", 0));
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON).content(visit)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        JsonNode page = objectMapper.readTree(mockMvc.perform(get("/api/v1/appointments")
                        .param("size", "50").header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString());
        long appointmentId = -1;
        for (JsonNode row : page.get("data").get("records")) {
            if ("synthetic headache".equals(row.get("chiefComplaint").asText())) {
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
                                "visitType", "FOLLOW_UP", "chiefComplaint", "synthetic headache",
                                "icd10Codes", "R51.9",
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
        assertEquals("R51.9", charge.get("icd10Codes").asText(),
                "the charge carries the visit's diagnosis code, not its chief complaint (R53-07)");

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

    /**
     * The conflict lookup answers "is this slot taken", and nothing else (R53-02). It
     * used to return full appointment records — patient name, chief complaint, notes —
     * to any doctor who asked about any doctor's calendar, which is a way around the
     * patient scope that the detail endpoints enforce.
     */
    @Test
    @Order(50)
    void conflictLookup_shouldDiscloseNoPatientDetails() throws Exception {
        // Appointment 201: patient 100 with doctor 2 at 2026-05-20T10:30.
        JsonNode conflicts = objectMapper.readTree(mockMvc.perform(get("/api/v1/appointments/conflicts")
                        .param("doctorId", "2").param("time", "2026-05-20T10:30:00")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("data");

        assertTrue(conflicts.size() >= 1, "the occupied slot must still be reported");
        for (JsonNode slot : conflicts) {
            Set<String> keys = new TreeSet<>();
            slot.fieldNames().forEachRemaining(keys::add);
            assertEquals(Set.of("appointmentTime", "durationMinutes"), keys,
                    "an availability answer carries occupancy, not the patient's record");
        }
    }

    /**
     * The guard against over-correcting: narrowing the *response* must not narrow the
     * *booking* check. An appointment belonging to a patient outside the caller's scope
     * still occupies the doctor's slot, so the booking is refused. The occupant is
     * removed afterwards: leaving it would silently widen doctor1's patient scope (the
     * scope is derived from exactly these rows) for every test that runs later.
     */
    @Test
    @Order(51)
    void anOccupantOutsideTheCallersScope_shouldStillBlockTheBooking() throws Exception {
        String time = LocalDateTime.now().plusDays(120).withNano(0).toString();
        String occupant = objectMapper.writeValueAsString(Map.of(
                "patientId", 103, "doctorId", 2, "appointmentTime", time,
                "description", "occupant outside doctor1's scope", "status", 0));
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON).content(occupant)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        try {
            String booking = objectMapper.writeValueAsString(Map.of(
                    "patientId", 100, "doctorId", 2, "appointmentTime", time,
                    "description", "same slot", "status", 0));
            mockMvc.perform(post("/api/v1/appointments")
                            .contentType(MediaType.APPLICATION_JSON).content(booking)
                            .header("Authorization", "Bearer " + doctorToken))
                    .andExpect(status().isConflict());
        } finally {
            deleteAppointmentAt(time);
        }
    }

    /**
     * Detail reads stay scoped, and this is the same patient record the conflict
     * lookup no longer names. The appointment belongs to doctor 3 — a doctor the
     * caller has no relationship with — so it sits outside doctor1's scope without
     * touching any seeded patient.
     */
    @Test
    @Order(52)
    void detailReadOutsideTheCallersScope_shouldBeForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Test R53-02 Probe", "mrn", "MRN-TEST-532")))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        long patientId = objectMapper.readTree(mockMvc.perform(get("/api/v1/patients")
                        .param("keyword", "MRN-TEST-532")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString())
                .get("data").get("records").get(0).get("id").asLong();

        String time = LocalDateTime.now().plusDays(130).withNano(0).toString();
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", patientId, "doctorId", 3, "appointmentTime", time,
                                "description", "another doctor's visit", "status", 0)))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        try {
            long appointmentId = appointmentIdAt(time);
            mockMvc.perform(get("/api/v1/appointments/" + appointmentId)
                            .header("Authorization", "Bearer " + doctorToken))
                    .andExpect(status().isForbidden());
        } finally {
            deleteAppointmentAt(time);
            mockMvc.perform(delete("/api/v1/patients/" + patientId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }
    }

    /**
     * A visit that has already started can still be completed (R53-06). The update path
     * rejected any past appointment time — even the one already recorded — so recording
     * the outcome of a visit required editing its start time, which is exactly what the
     * record must not allow.
     */
    @Test
    @Order(53)
    void completingAPastVisit_shouldSucceedWithoutChangingItsTime() throws Exception {
        String time = LocalDateTime.now().plusDays(150).withNano(0).toString();
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", 100, "doctorId", 2, "appointmentTime", time,
                                "visitType", "FOLLOW_UP", "chiefComplaint", "started already",
                                "icd10Codes", "R51.9", "cptCode", "99213", "status", 1)))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        long id = appointmentIdAt(time);
        String startedAt = LocalDateTime.now().minusMinutes(10).withNano(0).toString();
        jdbcTemplate.update("UPDATE appointment SET appointment_time = ? WHERE id = ?", startedAt, id);

        try {
            mockMvc.perform(put("/api/v1/appointments/" + id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "patientId", 100, "doctorId", 2, "appointmentTime", startedAt,
                                    "visitType", "FOLLOW_UP", "chiefComplaint", "started already",
                                    "icd10Codes", "R51.9", "cptCode", "99213", "status", 3)))
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());

            JsonNode charge = firstChargeFor(id);
            assertNotNull(charge, "completing the visit still produces its charge");
            assertEquals("R51.9", charge.get("icd10Codes").asText(),
                    "the charge carries the diagnosis code, not the chief complaint");
        } finally {
            jdbcTemplate.update("DELETE FROM charge WHERE appointment_id = ?", id);
            jdbcTemplate.update("DELETE FROM appointment WHERE id = ?", id);
        }
    }

    /** The future-time rule still applies to a real reschedule. */
    @Test
    @Order(54)
    void reschedulingIntoThePast_shouldStillBeRejected() throws Exception {
        String time = LocalDateTime.now().plusDays(160).withNano(0).toString();
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", 100, "doctorId", 2, "appointmentTime", time,
                                "visitType", "FOLLOW_UP", "description", "future booking", "status", 0)))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        long id = appointmentIdAt(time);

        try {
            mockMvc.perform(put("/api/v1/appointments/" + id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "patientId", 100, "doctorId", 2,
                                    "appointmentTime", LocalDateTime.now().minusDays(1).withNano(0).toString(),
                                    "visitType", "FOLLOW_UP", "description", "moved into the past",
                                    "status", 0)))
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isBadRequest());
        } finally {
            jdbcTemplate.update("DELETE FROM appointment WHERE id = ?", id);
        }
    }

    private JsonNode firstChargeFor(long appointmentId) throws Exception {
        JsonNode records = objectMapper.readTree(mockMvc.perform(get("/api/v1/charges")
                        .param("patientId", "100").param("size", "50")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString()).get("data").get("records");
        for (JsonNode row : records) {
            if (row.get("appointmentId") != null && row.get("appointmentId").asLong() == appointmentId) {
                return row;
            }
        }
        return null;
    }

    private long appointmentIdAt(String time) throws Exception {
        JsonNode records = objectMapper.readTree(mockMvc.perform(get("/api/v1/appointments")
                        .param("page", "1").param("size", "100")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString()).get("data").get("records");
        for (JsonNode row : records) {
            if (row.get("appointmentTime").asText().startsWith(time)) return row.get("id").asLong();
        }
        throw new IllegalStateException("no appointment at " + time);
    }

    private void deleteAppointmentAt(String time) throws Exception {
        mockMvc.perform(delete("/api/v1/appointments/" + appointmentIdAt(time))
                .header("Authorization", "Bearer " + adminToken));
    }

    // ──────────────────────────────────────────────────────
}
