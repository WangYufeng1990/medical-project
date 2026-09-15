package com.example.medical;

import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MvcResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Streaming CSV export endpoints. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExportIntegrationTest extends IntegrationTestSupport {

    private String adminToken = null;

    @BeforeAll
    void authenticate() throws Exception {
        adminToken = login("admin", "admin123");
    }

    // ──────────────────────────────────────────────────────

    @Test
    void exportPatients_shouldNotFail() throws Exception {
        // May return 200 or 429 (rate-limited); must not be 500
        MvcResult result = mockMvc.perform(get("/api/v1/export/patients")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn();
        assertNotEquals(500, result.getResponse().getStatus(),
                "Export patients should not return 500");
    }

    @Test
    void exportBills_shouldNotFail() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/export/bills")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn();
        assertNotEquals(500, result.getResponse().getStatus(),
                "Export bills should not return 500");
    }

    // ──────────────────────────────────────────────────────
}
