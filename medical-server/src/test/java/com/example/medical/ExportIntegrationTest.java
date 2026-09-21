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

    /**
     * The export reads through the owning services now, and the doctor scope is
     * applied in SQL rather than by skipping rows while paging. A doctor's file
     * must therefore contain exactly their own patients — the same set their
     * patient list shows — and nothing the admin file does not have.
     */
    @Test
    void exportPatients_asDoctor_shouldContainOnlyScopedPatients() throws Exception {
        String doctorToken = login("doctor1", "doctor123");

        String adminCsv = csv("/api/v1/export/patients", adminToken);
        String doctorCsv = csv("/api/v1/export/patients", doctorToken);

        java.util.List<String> doctorRows = dataRows(doctorCsv);
        java.util.List<String> adminRows = dataRows(adminCsv);

        assertFalse(doctorRows.isEmpty(), "doctor export should contain their own patients");
        assertTrue(adminRows.size() > doctorRows.size(), "admin export should be the superset");
        for (String row : doctorRows) {
            assertTrue(adminRows.contains(row), "doctor row not present in the admin export: " + row);
        }

        MvcResult list = mockMvc.perform(get("/api/v1/patients")
                        .param("page", "1").param("size", "200")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk()).andReturn();
        int listTotal = objectMapper.readTree(list.getResponse().getContentAsString())
                .get("data").get("total").asInt();
        assertEquals(listTotal, doctorRows.size(), "export row count vs the doctor's patient list");
    }

    /**
     * {@code StreamingResponseBody} starts async processing, so the body only
     * exists after an async dispatch — reading the first result directly yields an
     * empty string (which is why the two "shouldNotFail" tests above can never see
     * any content).
     */
    private String csv(String path, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(path)
                        .header("Authorization", "Bearer " + token))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static java.util.List<String> dataRows(String csv) {
        return csv.lines().skip(1).filter(l -> !l.isBlank()).toList();
    }

    // ──────────────────────────────────────────────────────
}
