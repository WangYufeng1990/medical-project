package com.example.medical;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MvcResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** LOINC catalog and lab trend analysis. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LabIntegrationTest extends IntegrationTestSupport {

    private String doctorToken = null;

    @BeforeAll
    void authenticate() throws Exception {
        doctorToken = login("doctor1", "doctor123");
    }

    // ──────────────────────────────────────────────────────

    @Test
    void loincCatalog_shouldReturnCodes() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/loinc/catalog")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(node.get("data").get(0).has("loincCode"));
    }

    @Test
    void loincPanel_shouldReturnGroupedCodes() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/loinc/panel/CBC")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(node.get("data").size() >= 5, "CBC panel should have 8 codes");
    }

    // ──────────────────────────────────────────────────────
    // ──────────────────────────────────────────────────────

    @Test
    void labTrend_shouldReturnObservationsByLoinc() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/patients/100/observations")
                        .param("loinc", "6690-2")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(node.get("data").get("records").isArray());
    }

    // ──────────────────────────────────────────────────────
}
