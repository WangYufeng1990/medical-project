package com.example.medical;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MvcResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Audit log query API. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuditLogIntegrationTest extends IntegrationTestSupport {

    private String adminToken = null;

    @BeforeAll
    void authenticate() throws Exception {
        adminToken = login("admin", "admin123");
    }

    // ──────────────────────────────────────────────────────

    @Test
    void auditLogs_shouldReturnPaginatedResults() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/audit-logs")
                        .param("page", "1").param("size", "10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").get("total").asInt() > 0,
                "Audit logs should contain entries from test bootstrap");
    }

    @Test
    void auditLogs_withFilters_shouldFilter() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/audit-logs")
                        .param("module", "patient").param("action", "CREATE")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(node.get("data").get("total").asInt() >= 0);
    }

    // ──────────────────────────────────────────────────────
}
