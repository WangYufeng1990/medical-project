package com.example.medical;

import com.fasterxml.jackson.databind.JsonNode;
import com.example.medical.module.chat.repository.MessageRepository;
import com.example.medical.module.chat.entity.Message;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Patient-doctor messaging, conversations and SSE. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChatIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MessageRepository messageRepository;

    private String adminToken, doctorToken, patientToken = null;

    @BeforeAll
    void authenticate() throws Exception {
        adminToken = login("admin", "admin123");
        doctorToken = login("doctor1", "doctor123");
        patientToken = patientLogin("patient1", "patient123");
    }

    // ──────────────────────────────────────────────────────

    @Test
    void conversations_asDoctor_shouldReturnConversations() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/messages/conversations")
                        .param("page", "1").param("size", "20")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").get("records").isArray());
    }

    @Test
    void getConversation_shouldReturnMessages() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/messages/100")
                        .param("partnerType", "PATIENT")
                        .param("page", "1").param("size", "50")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").get("records").isArray());
    }

    @Test
    void sendMessage_shouldSucceed() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "receiverId", 100,
                "receiverType", "PATIENT",
                "content", "Test message from doctor"
        ));
        mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());
    }

    @Test
    void sendMessage_withoutReceiverType_shouldReject() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "receiverId", 100,
                "content", "Test message without type"
        ));
        mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void patientConversations_shouldReturnConversations() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/patient/me/messages/conversations")
                        .param("page", "1").param("size", "20")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
    }

    @Test
    void patientSendMessage_shouldSucceed() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "receiverId", 2,
                "content", "Test message from patient"
        ));
        mockMvc.perform(post("/api/v1/patient/me/messages")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk());
    }

    @Test
    void patientChat_idSpaceCollision_shouldNotLeakStaffMessages() throws Exception {
        // R2-1 regression: patient 100 shares its numeric id with a hypothetical
        // staff user 100. A message "from staff 100 to patient 99" must never
        // surface in patient 100's inbox (the old bare-id query returned it).
        Message leak = new Message();
        leak.setSenderId(100L);
        leak.setSenderType("STAFF");
        leak.setReceiverId(99L);
        leak.setReceiverType("PATIENT");
        leak.setContent("confidential staff-to-other-patient message");
        leak.setIsRead(0);
        messageRepository.save(leak);

        MvcResult result = mockMvc.perform(get("/api/v1/patient/me/messages/conversations")
                        .param("page", "1").param("size", "20")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode records = node.get("data").get("records");
        // Patient 100 only talks with staff user 2 — the colliding staff-100 row must not appear.
        assertEquals(1, records.size());
        assertEquals(2, records.get(0).get("partnerId").asLong());
        assertEquals("STAFF", records.get(0).get("partnerType").asText());
    }

    // ── R2-2: DOCTOR patient scoping (patient 102 has no doctor-2 relationship) ──

    @Test
    void doctorVitals_outOfScopePatient_should403() throws Exception {
        mockMvc.perform(get("/api/v1/patients/102/vitals")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void doctorClinicalReads_outOfScopePatient_should403() throws Exception {
        mockMvc.perform(get("/api/v1/patients/102/history")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/patients/102/allergies")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/patients/102/observations")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/patients/102/problems")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/patients/102/care-plans")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/patients/102/immunizations")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/patients/102/case")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void doctorChargeList_shouldExcludeOutOfScopePatients() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/charges")
                        .param("page", "1").param("size", "50")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode r : node.get("data").get("records")) {
            assertNotEquals(102, r.get("patientId").asLong(),
                    "seed charge for patient 102 must not leak into doctor 2's list");
        }
    }

    @Test
    void doctorAppointmentDetail_outOfScope_should403() throws Exception {
        // Admin books an appointment for patient 102 (doctorId 1) so the row exists.
        String body = objectMapper.writeValueAsString(Map.of(
                "patientId", 102, "doctorId", 1,
                "appointmentTime", LocalDateTime.now().plusDays(5)
                        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "department", "Neurology"));
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Find the new appointment id via the admin list.
        MvcResult list = mockMvc.perform(get("/api/v1/appointments")
                        .param("patientId", "102")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode records = objectMapper.readTree(list.getResponse().getContentAsString())
                .get("data").get("records");
        long newId = records.get(0).get("id").asLong();

        // Doctor 2 (no relationship with patient 102) must get 403 on detail.
        mockMvc.perform(get("/api/v1/appointments/" + newId)
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());

        // In-scope control: appointment 200 belongs to patient 100 (doctor 2's own).
        mockMvc.perform(get("/api/v1/appointments/200")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());
    }

    @Test
    void adminReads_outOfScopeForDoctors_shouldStillSeeAll() throws Exception {
        mockMvc.perform(get("/api/v1/patients/102/vitals")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void emergencyAccess_shouldBypassDoctorScope() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("reason", "break-glass test"));
        MvcResult em = mockMvc.perform(post("/api/v1/emergency/access/102")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(em.getResponse().getContentAsString());
        String emToken = node.get("data").get("token").asText();

        // The emergency token carries patientId=102 — vitals for 102 must now be readable.
        mockMvc.perform(get("/api/v1/patients/102/vitals")
                        .header("Authorization", "Bearer " + emToken))
                .andExpect(status().isOk());
    }

    // ── R2-3: charge validation (Post-Round 44) ──

    @Test
    void createCharge_missingPatient_should400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "chargeAmount", 50.00, "cptCodes", "99213"));
        mockMvc.perform(post("/api/v1/charges")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCharge_valid_shouldSucceed_andRoundTripEncryptedNotes() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "patientId", 100, "chargeAmount", 50.00, "cptCodes", "99213",
                "notes", "encrypted smoke"));
        mockMvc.perform(post("/api/v1/charges")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patientId").value(100))
                .andExpect(jsonPath("$.data.notes").value("encrypted smoke"));
    }

    // ── Round 49 Item 3: FHIR read endpoints honor DOCTOR scope ──

    @Test
    void fhirRead_outOfScopePatient_should403() throws Exception {
        mockMvc.perform(get("/api/v1/fhir/Patient/102")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/fhir/Patient")
                        .param("_id", "102")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/fhir/Observation")
                        .param("patient", "102")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        // Out-of-scope observation id (patient 103), resolved via an admin search.
        MvcResult adminSearch = mockMvc.perform(get("/api/v1/fhir/Observation")
                        .param("patient", "103")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode entries = objectMapper.readTree(adminSearch.getResponse().getContentAsString())
                .get("entry");
        assertNotNull(entries, "seed observations for patient 103 expected");
        long obsId = Long.parseLong(entries.get(0).get("resource").get("id").asText());
        mockMvc.perform(get("/api/v1/fhir/Observation/" + obsId)
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void fhirRead_inScopeAndAdmin_shouldSucceed() throws Exception {
        mockMvc.perform(get("/api/v1/fhir/Patient/100")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/fhir/Patient/102")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/fhir/Observation")
                        .param("patient", "102")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void fhirRead_emergencyToken_shouldBypassDoctorScope() throws Exception {
        MvcResult em = mockMvc.perform(post("/api/v1/emergency/access/102")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "fhir break-glass")))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        String emToken = objectMapper.readTree(em.getResponse().getContentAsString())
                .get("data").get("token").asText();
        mockMvc.perform(get("/api/v1/fhir/Patient/102")
                        .header("Authorization", "Bearer " + emToken))
                .andExpect(status().isOk());
    }

    // ── Round 49 Item 1/2 regression: encrypted free-text round-trip ──

    @Test
    void createCarePlan_longEncryptedGoal_shouldRoundTrip() throws Exception {
        // ~250 plaintext chars — ciphertext (~560 hex) would truncate under
        // the old VARCHAR(200/500) widths, and the goal must survive DB
        // write + decrypt on read.
        String longGoal = "Patient goal: achieve and maintain HbA1c below 7.0 for the next six consecutive " +
                "months through dietary changes, weekly exercise routine, and compliance with the prescribed " +
                "oral antidiabetic medication regimen. Reassessment scheduled quarterly with dose adjustment " +
                "as indicated by continuous glucose monitoring trends.";
        MvcResult created = mockMvc.perform(post("/api/v1/patients/100/care-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Diabetes management",
                                "goal", longGoal,
                                "interventions", "Diet, exercise, medication",
                                "status", "ACTIVE",
                                "createdBy", 2)))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        long planId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("id").asLong();
        MvcResult list = mockMvc.perform(get("/api/v1/patients/100/care-plans")
                        .param("page", "1").param("size", "100")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode records = objectMapper.readTree(list.getResponse().getContentAsString())
                .get("data").get("records");
        JsonNode found = null;
        for (JsonNode r : records) {
            if (r.get("id").asLong() == planId) { found = r; break; }
        }
        assertNotNull(found, "created care plan must appear in the patient's list");
        assertEquals(longGoal, found.get("goal").asText(),
                "long encrypted goal must round-trip intact through DB");
    }

    @Test
    void createReferral_encryptedFields_shouldRoundTrip() throws Exception {
        String diagnosis = "Essential hypertension with recurrent headaches unresponsive to first-line therapy";
        String reason = "Patient reports bilateral throbbing headaches for the past three weeks; neurology consult needed";
        String notes = "PCP follow-up scheduled in two weeks";
        MvcResult created = mockMvc.perform(post("/api/v1/referrals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", 100,
                                "specialistName", "Dr. Neurology",
                                "specialty", "Neurology",
                                "diagnosis", diagnosis,
                                "reason", reason,
                                "notes", notes)))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        long referralId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("id").asLong();
        MvcResult list = mockMvc.perform(get("/api/v1/patients/100/referrals")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode records = objectMapper.readTree(list.getResponse().getContentAsString()).get("data");
        JsonNode found = null;
        for (JsonNode r : records) {
            if (r.get("id").asLong() == referralId) { found = r; break; }
        }
        assertNotNull(found, "created referral must appear in the patient's list");
        assertEquals(diagnosis, found.get("diagnosis").asText());
        assertEquals(reason, found.get("reason").asText());
        assertEquals(notes, found.get("notes").asText());
    }

    // ── Round 49: R2-2 create-endpoint gap — writes honor DOCTOR scope too ──

    @Test
    void doctorCreate_outOfScopePatient_should403() throws Exception {
        mockMvc.perform(post("/api/v1/patients/102/care-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "x", "goal", "y")))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/patients/102/problems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("snomedCode", "x", "snomedDisplay", "y")))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/patients/102/vitals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("systolicBp", 120)))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/patients/102/immunizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("vaccineName", "x")))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/patients/102/history")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("description", "x")))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/patients/102/allergies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("allergen", "x", "reaction", "y", "severity", "MILD")))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/referrals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("patientId", 102, "specialistName", "Dr. X")))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/prior-auths")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", 102, "authType", "MED", "itemName", "x",
                                "itemCode", "y", "insurancePayer", "z")))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
    }

    // ── Post-Round-49 review: newly encrypted free-text PHI round-trip ──

    @Test
    void createImmunization_longNotes_shouldRoundTrip() throws Exception {
        String longNotes = "Post-vaccination notes (encrypted): patient monitored 15 minutes, " + "x".repeat(200);
        String body = objectMapper.writeValueAsString(Map.of(
                "vaccineName", "RSV vaccine",
                "administrationDate", "2026-01-15",
                "lotNumber", "RS2026-001",
                "manufacturer", "GSK",
                "doseNumber", "1st dose",
                "site", "left arm",
                "route", "intramuscular",
                "notes", longNotes
        ));
        mockMvc.perform(post("/api/v1/patients/102/immunizations")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        MvcResult result = mockMvc.perform(get("/api/v1/patients/102/immunizations")
                        .param("page", "1").param("size", "50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode records = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("records");
        boolean found = false;
        for (JsonNode n : records) {
            if ("RSV vaccine".equals(n.get("vaccineName").asText())) {
                assertEquals(longNotes, n.get("notes").asText());
                found = true;
            }
        }
        assertTrue(found, "created immunization not found in list");
    }

    @Test
    void refillAndEmergency_encryptedFields_shouldRoundTrip() throws Exception {
        // Refill reason: patient-created free text round-trips through encryption.
        String refillReason = "Long refill reason (encrypted): lost the bottle, " + "y".repeat(200);
        // Prescription 301 is active (300 is completed in the test fixture) —
        // refill requires an active prescription (Review III C8).
        mockMvc.perform(post("/api/v1/patient/me/refill-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("prescriptionId", 301, "reason", refillReason)))
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk());
        MvcResult mine = mockMvc.perform(get("/api/v1/patient/me/refill-requests")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list = objectMapper.readTree(mine.getResponse().getContentAsString()).get("data");
        assertTrue(list.size() > 0, "no refill requests listed");
        assertEquals(refillReason, list.get(0).get("reason").asText());

        // Emergency access reason: round-trips through encryption (admin history read-back).
        String emReason = "Emergency reason (encrypted): patient unresponsive, " + "z".repeat(200);
        mockMvc.perform(post("/api/v1/emergency/access/102")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", emReason)))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());
        MvcResult history = mockMvc.perform(get("/api/v1/emergency/history")
                        .param("patientId", "102")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode entries = objectMapper.readTree(history.getResponse().getContentAsString()).get("data");
        boolean found = false;
        for (JsonNode n : entries) {
            if (emReason.equals(n.get("reason").asText())) { found = true; break; }
        }
        assertTrue(found, "emergency access reason did not round-trip");
    }

    // ──────────────────────────────────────────────────────
}
