package com.example.medical;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MvcResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** FHIR R4 patient, case and observation endpoints. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FhirIntegrationTest extends IntegrationTestSupport {

    private String doctorToken = null;

    @BeforeAll
    void authenticate() throws Exception {
        doctorToken = login("doctor1", "doctor123");
    }

    // ──────────────────────────────────────────────────────

    @Test
    void fhirMetadata_shouldReturnCapabilityStatement() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/fhir/metadata"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/fhir+json"))
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("CapabilityStatement", node.get("resourceType").asText());
    }

    @Test
    void patientCase_shouldReturnFhirBundle() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/patients/100/case")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/fhir+json"))
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("Bundle", node.get("resourceType").asText());
    }

    // ──────────────────────────────────────────────────────
    // ──────────────────────────────────────────────────────

    @Test
    void fhirPatientById_shouldReturnFhirPatient() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/fhir/Patient/100")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/fhir+json"))
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("Patient", node.get("resourceType").asText());
    }

    @Test
    void fhirPatientSearch_shouldReturnBundle() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/fhir/Patient")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/fhir+json"))
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("Bundle", node.get("resourceType").asText());
    }

    @Test
    void fhirObservationSearch_shouldReturnBundle() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/fhir/Observation")
                        .param("patient", "100")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/fhir+json"))
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("Bundle", node.get("resourceType").asText());
        assertTrue(node.get("total").asInt() >= 0);
    }

    @Test
    void fhirObservationSearch_noPatient_shouldReturnBundle() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/fhir/Observation")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("Bundle", node.get("resourceType").asText());
    }

    // ──────────────────────────────────────────────────────
}
