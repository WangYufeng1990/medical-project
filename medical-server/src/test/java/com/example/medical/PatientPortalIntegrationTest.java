package com.example.medical;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Patient portal self-service endpoints. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PatientPortalIntegrationTest extends IntegrationTestSupport {

    private String patientToken = null;
    private String adminToken = null;

    @BeforeAll
    void authenticate() throws Exception {
        patientToken = patientLogin("patient1", "patient123");
        adminToken = login("admin", "admin123");
    }

    /**
     * Books an appointment {@code daysAhead} out for {@code patientId} and returns
     * its id. Callers pass distinct offsets: two bookings for the same doctor
     * within 30 minutes collide (409), and every seeded appointment is in the past.
     */
    private long bookAppointment(long patientId, long doctorId, int daysAhead) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("patientId", patientId);
        body.put("doctorId", doctorId);
        body.put("appointmentTime", LocalDateTime.now().plusDays(daysAhead).withNano(0).toString());
        body.put("visitType", "FOLLOW_UP");
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/v1/appointments")
                        .param("patientId", String.valueOf(patientId))
                        .param("page", "1").param("size", "200")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode records = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("records");
        long latestId = 0;
        String latestTime = "";
        for (JsonNode row : records) {
            if (row.get("appointmentTime").asText().compareTo(latestTime) > 0) {
                latestTime = row.get("appointmentTime").asText();
                latestId = row.get("id").asLong();
            }
        }
        assertTrue(latestId > 0, "appointment was not created");
        return latestId;
    }

    private JsonNode myAppointments() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/patient/me/appointments")
                        .param("page", "1").param("size", "50")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("records");
    }

    private JsonNode findAppointment(JsonNode records, long id) {
        for (JsonNode row : records) {
            if (row.get("id").asLong() == id) return row;
        }
        return null;
    }

    // ──────────────────────────────────────────────────────

    @Test
    void patientGetMyProfile_shouldReturnProfile() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/patient/me")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertNotNull(node.get("data").get("name").asText());
    }

    @Test
    void patientGetMyAppointments_shouldReturnAppointments() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/patient/me/appointments")
                        .param("page", "1").param("size", "10")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").get("total").asInt() >= 1);
    }

    @Test
    void patientGetMyPrescriptions_shouldReturnPrescriptions() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/patient/me/prescriptions")
                        .param("page", "1").param("size", "10")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
    }

    @Test
    void patientGetMyBills_shouldReturnBills() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/patient/me/bills")
                        .param("page", "1").param("size", "10")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
    }

    // ──────────────────────────────────────────────────────
    // ──────────────────────────────────────────────────────

    @Test
    void patientUpdateOwnProfile_shouldSucceed() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "phoneMobile", "312-555-7777",
                "email", "james.updated@email.com"
        ));
        mockMvc.perform(put("/api/v1/patient/me")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk());
    }

    @Test
    void patientUpdateOwnProfile_nameBlocked_shouldBeIgnored() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Hacked Name"
        ));
        mockMvc.perform(put("/api/v1/patient/me")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk());
        // verify name unchanged
        MvcResult result = mockMvc.perform(get("/api/v1/patient/me")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk()).andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("James Anderson", node.get("data").get("name").asText());
    }

    // ── cancellation rules (AppointmentService.cancelByPatient) ──

    @Test
    void patientCancelOwnAppointment_shouldSucceedAndBeOneShot() throws Exception {
        long id = bookAppointment(100L, 2L, 7);

        mockMvc.perform(put("/api/v1/patient/me/appointments/" + id + "/cancel")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk());

        JsonNode cancelled = findAppointment(myAppointments(), id);
        assertNotNull(cancelled);
        assertEquals(2, cancelled.get("status").asInt());

        MvcResult second = mockMvc.perform(put("/api/v1/patient/me/appointments/" + id + "/cancel")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isConflict())
                .andReturn();
        assertEquals("Appointment already cancelled or completed",
                objectMapper.readTree(second.getResponse().getContentAsString()).get("message").asText());
    }

    @Test
    void patientCancelOtherPatientsAppointment_shouldBeForbidden() throws Exception {
        long id = bookAppointment(101L, 2L, 8);
        mockMvc.perform(put("/api/v1/patient/me/appointments/" + id + "/cancel")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void patientCancelPastAppointment_shouldConflict() throws Exception {
        // Seed 202 belongs to patient 100, is still SCHEDULED, and is dated in the past.
        MvcResult result = mockMvc.perform(put("/api/v1/patient/me/appointments/202/cancel")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isConflict())
                .andReturn();
        assertEquals("Cannot cancel past appointments",
                objectMapper.readTree(result.getResponse().getContentAsString()).get("message").asText());
    }

    // ── payment rules (BillService.payByPatient) ──

    @Test
    void patientPayOwnBill_shouldSettleIt() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "paymentAmount", 37.80, "paymentMethod", "CREDIT_CARD"));
        mockMvc.perform(put("/api/v1/patient/me/bills/501/pay")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/v1/patient/me/bills")
                        .param("page", "1").param("size", "50")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk()).andReturn();
        JsonNode records = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("records");
        JsonNode bill = null;
        for (JsonNode row : records) {
            if (row.get("id").asLong() == 501L) bill = row;
        }
        assertNotNull(bill);
        assertEquals("PAID", bill.get("claimStatus").asText());
    }

    @Test
    void patientPayOtherPatientsBill_shouldBeForbidden() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "paymentAmount", 5.00, "paymentMethod", "CREDIT_CARD"));
        mockMvc.perform(put("/api/v1/patient/me/bills/502/pay")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isForbidden());
    }

    // ── credential rules (PatientAccountService.changePassword) ──

    @Test
    void patientChangePassword_wrongOldPassword_shouldBeRejected() throws Exception {
        MvcResult result = changePassword("not-my-password", "N3wPatientPass!")
                .andExpect(status().isBadRequest())
                .andReturn();
        assertEquals("Old password is incorrect",
                objectMapper.readTree(result.getResponse().getContentAsString()).get("message").asText());
    }

    @Test
    void patientChangePassword_shouldApplyAndBlockReuse() throws Exception {
        changePassword("patient123", "Pw!One2345").andExpect(status().isOk());
        assertNotNull(patientLogin("patient1", "Pw!One2345"));

        // Second change makes Pw!One2345 part of the recorded history.
        changePassword("Pw!One2345", "Pw!Two2345").andExpect(status().isOk());

        MvcResult result = changePassword("Pw!Two2345", "Pw!One2345")
                .andExpect(status().isBadRequest())
                .andReturn();
        assertEquals("New password must not match any of the last 3 passwords",
                objectMapper.readTree(result.getResponse().getContentAsString()).get("message").asText());
    }

    /**
     * The export is a document the patient downloads under HIPAA 45 CFR 164.524,
     * so its shape is pinned here: M8.6 rewired the assembly from entities to
     * VOs and the file had to come out byte-identical (verified live by diffing
     * the response before and after, ignoring {@code exportDate}).
     */
    @Test
    void exportMyData_shouldKeepTheRightOfAccessDocumentShape() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/patient/me/export")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");

        assertEquals(Set.of("appointments", "bills", "dataUseNotice", "demographics", "exportDate",
                "prescriptions"), fieldNames(data));
        assertNotNull(data.get("dataUseNotice").asText());
        assertFalse(data.get("demographics").get("name").asText().isBlank());

        JsonNode appointment = data.get("appointments").get(0);
        assertEquals(Set.of("appointmentTime", "chiefComplaint", "department", "description", "id",
                "status", "visitType"), fieldNames(appointment));
        assertEquals(Set.of("diagnosis", "icd10Codes", "id", "items", "prescriptionDate", "rxStatus"),
                fieldNames(data.get("prescriptions").get(0)));
        assertEquals(Set.of("daysSupply", "dosage", "drugName", "frequency", "refills", "sig"),
                fieldNames(data.get("prescriptions").get(0).get("items").get(0)));
        assertEquals(Set.of("billType", "claimStatus", "cptCodes", "icd10Codes", "id",
                "insurancePayerName", "patientResponsibility", "totalCharge"),
                fieldNames(data.get("bills").get(0)));

        for (String section : new String[]{"appointments", "prescriptions", "bills"}) {
            for (JsonNode row : data.get(section)) {
                for (String leaked : new String[]{"isDeleted", "version", "updateTime"}) {
                    assertFalse(row.has(leaked), section + " exposes " + leaked);
                }
            }
        }
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new TreeSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private ResultActions changePassword(String oldPassword, String newPassword)
            throws Exception {
        Map<String, String> body = Map.of("oldPassword", oldPassword, "newPassword", newPassword);
        return mockMvc.perform(put("/api/v1/patient/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("Authorization", "Bearer " + patientToken));
    }

    /**
     * The portal surface is six controllers now (M8.4), each carrying its own
     * class-level {@code @PreAuthorize}. One endpoint per controller is probed
     * with a staff token: a class that forgets the annotation answers 200 here
     * instead of 403, which is how the split would silently open the portal.
     */
    @Test
    void staffToken_shouldBeRejectedOnEveryPortalController() throws Exception {
        String[] oneEndpointPerController = {
                "/api/v1/patient/me",
                "/api/v1/patient/me/observations",
                "/api/v1/patient/me/appointments",
                "/api/v1/patient/me/prescriptions",
                "/api/v1/patient/me/bills",
                "/api/v1/patient/me/export",
        };
        for (String path : oneEndpointPerController) {
            MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + adminToken))
                    .andReturn();
            assertEquals(403, result.getResponse().getStatus(),
                    path + " answered " + result.getResponse().getStatus()
                            + " for a staff token: " + result.getResponse().getContentAsString());
        }
    }
}
