package com.example.medical;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Break-glass emergency access.
 * <p>
 * The tests here form a deliberate sequence (create → update → delete, or a claim
 * lifecycle), so this class keeps an explicit order. Ordering is class-local: the
 * class still runs on its own because cleanup-test-data.sql restores the seeded
 * state before it starts.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EmergencyAccessIntegrationTest extends IntegrationTestSupport {

    private String adminToken, doctorToken = null;

    @BeforeAll
    void authenticate() throws Exception {
        adminToken = login("admin", "admin123");
        doctorToken = login("doctor1", "doctor123");
    }

    // ──────────────────────────────────────────────────────

    @Test
    @Order(107)
    void emergencyAccess_shouldReturnToken() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "reason", "Patient unconscious in ER — need allergy history immediately"
        ));
        MvcResult result = mockMvc.perform(post("/api/v1/emergency/access/100")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertNotNull(node.get("data").get("token"));
        assertEquals(100, node.get("data").get("patientId").asLong());
    }

    @Test
    @Order(108)
    void emergencyHistory_shouldReturnRecords() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/emergency/history")
                        .param("patientId", "100")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(node.get("data").isArray());
        assertTrue(node.get("data").size() >= 1);
    }

    // ──────────────────────────────────────────────────────
}
