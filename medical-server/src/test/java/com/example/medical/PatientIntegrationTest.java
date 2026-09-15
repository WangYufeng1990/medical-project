package com.example.medical;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Patient module: patient CRUD, history, allergies and consent.
 * <p>
 * The tests here form a deliberate sequence (create → update → delete, or a claim
 * lifecycle), so this class keeps an explicit order. Ordering is class-local: the
 * class still runs on its own because cleanup-test-data.sql restores the seeded
 * state before it starts.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PatientIntegrationTest extends IntegrationTestSupport {

    private String adminToken, doctorToken, patientToken = null;

    @BeforeAll
    void authenticate() throws Exception {
        adminToken = login("admin", "admin123");
        doctorToken = login("doctor1", "doctor123");
        patientToken = patientLogin("patient1", "patient123");
    }

    // ──────────────────────────────────────────────────────

    @Test
    @Order(31)
    void patientPage_asAdmin_shouldReturnPaginatedPatients() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/patients")
                        .param("page", "1").param("size", "10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").get("total").asInt() >= 3);
    }

    @Test
    @Order(32)
    void patientPage_asDoctor_shouldSucceed() throws Exception {
        mockMvc.perform(get("/api/v1/patients")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(33)
    void patientPage_asPatient_shouldBeDenied() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/patients")
                        .header("Authorization", "Bearer " + patientToken))
                .andReturn();
        assertNotEquals(200, result.getResponse().getStatus(),
                "Patient should not access admin/doctor patients endpoint");
    }

    @Test
    @Order(34)
    void getPatientById_shouldReturnPatient() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/patients/100")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertEquals("MRN-10001", node.get("data").get("mrn").asText());
        // PHI fields should be decrypted
        assertNotNull(node.get("data").get("name").asText());
    }

    @Test
    @Order(35)
    void getPatientById_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/patients/9999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(36)
    void createPatient_shouldSucceed() throws Exception {
        Map<String, Object> patientData = new LinkedHashMap<>();
        patientData.put("name", "Test Patient");
        patientData.put("mrn", "MRN-TEST-001");
        patientData.put("dateOfBirth", "2000-01-15");
        patientData.put("sexAtBirth", "F");
        patientData.put("genderIdentity", "Female");
        patientData.put("race", "Asian");
        patientData.put("ethnicity", "Not Hispanic or Latino");
        patientData.put("preferredLanguage", "en");
        patientData.put("maritalStatus", "Single");
        patientData.put("patientStatus", "active");
        patientData.put("primaryCareProvider", "Dr. Sarah Mitchell");
        patientData.put("phoneMobile", "312-555-1111");
        patientData.put("email", "test.patient@email.com");
        patientData.put("addressLine1", "100 Test St");
        patientData.put("city", "Chicago");
        patientData.put("state", "IL");
        patientData.put("zipCode", "60601");
        patientData.put("emergencyContactName", "Emergency Contact");
        patientData.put("emergencyContactPhone", "312-555-2222");
        patientData.put("emergencyContactRelation", "Spouse");
        patientData.put("insurancePayer", "Test Insurance");
        patientData.put("insuranceMemberId", "TST-12345");
        patientData.put("insuranceGroupNumber", "GRP-001");
        patientData.put("medicalHistory", "None");
        patientData.put("allergies", "None");
        String body = objectMapper.writeValueAsString(patientData);
        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(37)
    void createPatient_missingMrn_shouldReturn400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", "No MRN"));
        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(38)
    void updatePatient_shouldSucceed() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Test Patient Updated",
                "mrn", "MRN-TEST-001-UPD",
                "patientStatus", "active",
                "phoneMobile", "312-555-9999"
        ));
        mockMvc.perform(put("/api/v1/patients/103")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(39)
    void deletePatient_asDoctor_shouldBeDenied() throws Exception {
        MvcResult result = mockMvc.perform(delete("/api/v1/patients/103")
                        .header("Authorization", "Bearer " + doctorToken))
                .andReturn();
        assertNotEquals(200, result.getResponse().getStatus(),
                "Doctor should not be able to delete patients");
    }

    @Test
    @Order(40)
    void deletePatient_asAdmin_shouldSucceed() throws Exception {
        mockMvc.perform(delete("/api/v1/patients/103")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(41)
    void patientSearch_byKeyword_shouldFilter() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/patients")
                        .param("keyword", "MRN-10001")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(1, node.get("data").get("total").asInt());
    }

    // ──────────────────────────────────────────────────────
    // ──────────────────────────────────────────────────────

    @Test
    @Order(103)
    void consent_create_shouldSucceed() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "consentType", "TREATMENT",
                "scope", "general",
                "patientId", 100
        ));
        mockMvc.perform(post("/api/v1/consent")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(104)
    void consent_list_shouldReturnForPatient() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/consent")
                        .param("patientId", "100")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(node.get("data").isArray());
        assertTrue(node.get("data").size() >= 1);
    }

    @Test
    @Order(105)
    void consent_revoke_shouldSucceed() throws Exception {
        // revoke the one just created (id=1)
        mockMvc.perform(put("/api/v1/consent/1/revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(106)
    void patientConsent_selfService_shouldReturnRecords() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/patient/me/consent")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(node.get("data").isArray());
    }

    // ──────────────────────────────────────────────────────
}
