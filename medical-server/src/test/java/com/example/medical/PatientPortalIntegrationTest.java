package com.example.medical;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Patient portal self-service endpoints. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PatientPortalIntegrationTest extends IntegrationTestSupport {

    private String patientToken = null;

    @BeforeAll
    void authenticate() throws Exception {
        patientToken = patientLogin("patient1", "patient123");
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
}
