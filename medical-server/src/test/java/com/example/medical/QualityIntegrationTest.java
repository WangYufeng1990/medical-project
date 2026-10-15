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

    private String adminToken, doctorToken = null;

    @BeforeAll
    void authenticate() throws Exception {
        adminToken = login("admin", "admin123");
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

    /**
     * The counters, not just their shape (52.6). A report endpoint that returns a
     * number passes the test above whatever the number is, so the three measures are
     * pinned here to what the seeded patients actually compute — which is also what
     * makes this slice's claim ("the read model changes where the data comes from,
     * not what it says") checkable.
     * <p>
     * The seed: patient 100 (M, 28) and 103 (F, 71) carry diabetes and hypertension
     * in their history; 101 (F, 35) and 102 (M, 45) carry neither. Latest values —
     * 100: HbA1c 7.8, BP 132/85; 103: HbA1c 8.5, BP 145/92, mammogram 2026-03-15.
     */
    @Test
    void qualityReports_shouldMatchTheSeededCounters() throws Exception {
        // CMS122 — HbA1c control in diabetics 18-75: two diabetics, both controlled (<= 9%).
        assertCounters("CMS122v11", 2, 0, 2, 2, 100.0);
        // CMS125 — breast cancer screening in women 50-74: only patient 103, screened.
        assertCounters("CMS125v11", 1, 0, 1, 1, 100.0);
        // CMS165 — BP control in hypertensives 18-85: 103's latest 145/92 is not controlled.
        assertCounters("CMS165v11", 2, 0, 2, 1, 50.0);
    }

    private void assertCounters(String cmsId, long denominator, long exclusions,
                                long eligibleDenominator, long numerator, double rate) throws Exception {
        JsonNode data = objectMapper.readTree(mockMvc.perform(
                        post("/api/v1/quality/measures/" + cmsId + "/calculate")
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("data");

        assertEquals(10, data.size(), "the report carries ten keys");
        assertEquals(denominator, data.get("denominator").asLong(), cmsId + " denominator");
        assertEquals(exclusions, data.get("exclusions").asLong(), cmsId + " exclusions");
        assertEquals(eligibleDenominator, data.get("eligibleDenominator").asLong(), cmsId + " eligible");
        assertEquals(numerator, data.get("numerator").asLong(), cmsId + " numerator");
        assertEquals(rate, data.get("performanceRate").asDouble(), 0.001, cmsId + " rate");
    }

    // ──────────────────────────────────────────────────────
}
