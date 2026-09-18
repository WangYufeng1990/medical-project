package com.example.medical;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import java.time.LocalDate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Bill and insurance claim lifecycle.
 * <p>
 * The tests here form a deliberate sequence (create → update → delete, or a claim
 * lifecycle), so this class keeps an explicit order. Ordering is class-local: the
 * class still runs on its own because cleanup-test-data.sql restores the seeded
 * state before it starts.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BillingIntegrationTest extends IntegrationTestSupport {

    private String adminToken = null;

    @BeforeAll
    void authenticate() throws Exception {
        adminToken = login("admin", "admin123");
    }

    // ──────────────────────────────────────────────────────

    @Test
    @Order(55)
    void billPage_shouldReturnPaginated() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/bills")
                        .param("page", "1").param("size", "10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").get("total").asInt() >= 3);
    }

    @Test
    @Order(56)
    void billPage_withStatusFilter_shouldFilter() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/bills")
                        .param("claimStatus", "PAID")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(2, node.get("data").get("total").asInt());
    }

    @Test
    @Order(57)
    void getBillById_shouldReturnBill() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/bills/500")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertNotNull(node.get("data").get("patientName").asText());
        assertEquals("PAID", node.get("data").get("claimStatus").asText());
    }

    @Test
    @Order(58)
    void createBill_shouldCreateDraftBill() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "patientId", 100,
                "prescriptionId", 300,
                "totalCharge", 150.00,
                "billType", "PROFESSIONAL",
                "cptCodes", "99213",
                "icd10Codes", "J06.9",
                "placeOfServiceCode", "11",
                "billingProviderNpi", "1234567890",
                "insurancePayerName", "Blue Cross Blue Shield"
        ));
        mockMvc.perform(post("/api/v1/bills")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(59)
    void submitBill_shouldChangeStatusToSubmitted() throws Exception {
        mockMvc.perform(put("/api/v1/bills/503/submit")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/v1/bills/503")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("SUBMITTED", node.get("data").get("claimStatus").asText());
    }

    @Test
    @Order(60)
    void submitBill_notDraft_shouldReturn409() throws Exception {
        // Bill 500 is PAID, not DRAFT
        mockMvc.perform(put("/api/v1/bills/500/submit")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(61)
    void adjudicateBill_shouldCalculatePatientResponsibility() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "adjustment", 50.00,
                "insurancePayment", 70.00,
                "claimNumber", "BCBS-CLM-TEST",
                "adjudicationDate", LocalDate.now().toString()
        ));
        mockMvc.perform(put("/api/v1/bills/503/adjudicate")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/v1/bills/503")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        // totalCharge=150, adjustment=50, insurancePayment=70, patientResp=150-50-70=30 -> PENDING
        assertEquals("PENDING", node.get("data").get("claimStatus").asText());
        assertEquals(30.00, node.get("data").get("patientResponsibility").asDouble(), 0.01);
    }

    @Test
    @Order(62)
    void payBill_shouldRecordPayment() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "paymentAmount", 30.00,
                "paymentMethod", "CREDIT_CARD"
        ));
        mockMvc.perform(put("/api/v1/bills/503/pay")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/v1/bills/503")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("PAID", node.get("data").get("claimStatus").asText());
        assertEquals(30.00, node.get("data").get("patientPaidAmount").asDouble(), 0.01);
        assertNotNull(node.get("data").get("payTime").asText());
    }

    @Test
    @Order(63)
    void payBill_alreadyPaid_shouldReturn409() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "paymentAmount", 10.00,
                "paymentMethod", "CASH"
        ));
        mockMvc.perform(put("/api/v1/bills/503/pay")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(64)
    void denyBill_shouldSetToDenied() throws Exception {
        // Create a fresh bill first, submit it, then deny
        String body = objectMapper.writeValueAsString(Map.of(
                "patientId", 100,
                "totalCharge", 50.00
        ));
        MvcResult createResult = mockMvc.perform(post("/api/v1/bills")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        // Submit it
        mockMvc.perform(put("/api/v1/bills/504/submit")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Deny
        String denyBody = objectMapper.writeValueAsString(Map.of("reason", "Service not covered"));
        mockMvc.perform(put("/api/v1/bills/504/deny")
                        .contentType(MediaType.APPLICATION_JSON).content(denyBody)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/v1/bills/504")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("DENIED", node.get("data").get("claimStatus").asText());
    }

    @Test
    @Order(65)
    void deleteBill_asAdmin_shouldSucceed() throws Exception {
        mockMvc.perform(delete("/api/v1/bills/504")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // ── charge → bill conversion ────────────────────────────

    private long createCharge(long patientId, long appointmentId, double amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/charges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", patientId,
                                "appointmentId", appointmentId,
                                "chargeAmount", amount,
                                "cptCodes", "99213")))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    @Test
    @Order(70)
    void convertCharge_shouldRefuseWhenTheVisitIsAlreadyBilled() throws Exception {
        // Appointment 201 already carries seeded bill 500, so converting a charge
        // for it must not produce a second bill for the same visit.
        long chargeId = createCharge(100L, 201L, 20.85);
        MvcResult result = mockMvc.perform(put("/api/v1/charges/" + chargeId + "/convert")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andReturn();
        String message = objectMapper.readTree(result.getResponse().getContentAsString()).get("message").asText();
        assertTrue(message.contains("already billed"), message);
        assertTrue(message.contains("500"), message);
    }

    @Test
    @Order(71)
    void convertCharge_shouldCarryTheAppointmentOntoTheBill() throws Exception {
        // Appointment 203 has no bill yet.
        long chargeId = createCharge(100L, 203L, 45.00);
        MvcResult result = mockMvc.perform(put("/api/v1/charges/" + chargeId + "/convert")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode bill = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertEquals(203L, bill.get("appointmentId").asLong());
        assertEquals("DRAFT", bill.get("claimStatus").asText());
        assertEquals(45.00, bill.get("totalCharge").asDouble());
    }

    // ──────────────────────────────────────────────────────
}
