package com.example.medical;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MvcResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Dashboard aggregate statistics. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DashboardIntegrationTest extends IntegrationTestSupport {

    private String adminToken, doctorToken, patientToken = null;

    @BeforeAll
    void authenticate() throws Exception {
        adminToken = login("admin", "admin123");
        doctorToken = login("doctor1", "doctor123");
        patientToken = patientLogin("patient1", "patient123");
    }

    // ──────────────────────────────────────────────────────

    @Test
    void dashboardStats_asAdmin_shouldReturnStats() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/dashboard/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").get("totalPatients").asInt() >= 0);
        assertTrue(node.get("data").get("appointmentStatusDistribution").isArray());
        assertTrue(node.get("data").get("revenueTrend").isArray());
    }

    @Test
    void dashboardStats_asDoctor_shouldSucceed() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/stats")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());
    }

    /**
     * The dashboard runs raw SQL, so doctor scoping is not free. It used to count
     * every patient while /patients showed only the doctor's own — the counters
     * must agree with the list the caller can actually open.
     */
    @Test
    void dashboardStats_shouldAgreeWithTheListsForTheSameCaller() throws Exception {
        assertCountersMatchLists(adminToken);
        assertCountersMatchLists(doctorToken);
    }

    private void assertCountersMatchLists(String token) throws Exception {
        JsonNode stats = objectMapper.readTree(mockMvc.perform(get("/api/v1/dashboard/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("data");

        assertEquals(listTotal("/api/v1/patients", token),
                stats.get("totalPatients").asInt(), "totalPatients vs /patients");
        assertEquals(listTotal("/api/v1/bills?claimStatus=PENDING", token),
                stats.get("pendingBills").asInt(), "pendingBills vs /bills?claimStatus=PENDING");
        assertEquals(listTotal("/api/v1/appointments?status=0", token),
                stats.get("scheduledAppointments").asInt(), "scheduledAppointments vs /appointments?status=0");
    }

    private int listTotal(String path, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(path)
                        .param("page", "1").param("size", "200")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("total").asInt();
    }

    @Test
    void dashboardStats_asPatient_shouldBeDenied() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/dashboard/stats")
                        .header("Authorization", "Bearer " + patientToken))
                .andReturn();
        assertNotEquals(200, result.getResponse().getStatus(),
                "Patient should not access dashboard stats");
    }

    // ──────────────────────────────────────────────────────
}
