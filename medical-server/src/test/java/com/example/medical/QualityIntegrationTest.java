package com.example.medical;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MvcResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** CMS eCQM quality measure calculation. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QualityIntegrationTest extends IntegrationTestSupport {

    private String doctorToken = null;

    @BeforeAll
    void authenticate() throws Exception {
        doctorToken = login("doctor1", "doctor123");
    }

    // ──────────────────────────────────────────────────────

    @Test
    void qualityMeasures_shouldListDefinitions() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/quality/measures")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(node.get("data").isArray());
        assertTrue(node.get("data").size() >= 3, "Seed data should have 3 CMS measures");
    }

    @Test
    void qualityReport_shouldCalculatePerformance() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/quality/measures/CMS122v11/report")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("CMS122v11", node.get("data").get("cmsId").asText());
        assertTrue(node.get("data").get("performanceRate").isNumber());
    }

    // ──────────────────────────────────────────────────────
}
