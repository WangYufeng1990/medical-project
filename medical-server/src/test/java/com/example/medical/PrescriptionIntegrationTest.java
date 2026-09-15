package com.example.medical;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Prescription CRUD, CDS checks, pharmacy directory and NCPDP transmit. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PrescriptionIntegrationTest extends IntegrationTestSupport {

    private String adminToken, doctorToken = null;

    @BeforeAll
    void authenticate() throws Exception {
        adminToken = login("admin", "admin123");
        doctorToken = login("doctor1", "doctor123");
    }

    // ──────────────────────────────────────────────────────

    @Test
    void prescriptionPage_shouldReturnPaginated() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/prescriptions")
                        .param("page", "1").param("size", "10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").get("total").asInt() >= 3);
    }

    @Test
    void getPrescriptionById_shouldReturnWithItems() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/prescriptions/300")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").get("items").isArray());
        assertTrue(node.get("data").get("items").size() >= 2);
    }

    @Test
    void createPrescription_shouldSucceed() throws Exception {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("drugName", "Test Drug");
        item.put("specification", "100mg");
        item.put("dosage", "100mg");
        item.put("route", "PO");
        item.put("frequency", "TID");
        item.put("sig", "Take as directed");
        item.put("duration", 7);
        item.put("daysSupply", 7);
        item.put("quantity", 21);
        item.put("refills", 0);
        item.put("daw", 0);
        item.put("unitPrice", 1.50);

        String body = objectMapper.writeValueAsString(Map.of(
                "patientId", 100,
                "doctorId", 2,
                "diagnosis", "Test Diagnosis",
                "icd10Codes", "J00",
                "prescriptionDate", LocalDate.now().toString(),
                "prescriptionType", "MEDICATION",
                "items", List.of(item)
        ));
        mockMvc.perform(post("/api/v1/prescriptions")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void createPrescription_withoutItems_shouldReturn400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "patientId", 100,
                "doctorId", 2,
                "diagnosis", "No items",
                "items", List.of()
        ));
        mockMvc.perform(post("/api/v1/prescriptions")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletePrescription_asAdmin_shouldSucceed() throws Exception {
        mockMvc.perform(delete("/api/v1/prescriptions/303")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // ──────────────────────────────────────────────────────
    // ──────────────────────────────────────────────────────

    @Test
    void cdsCheck_shouldReturnResult() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "patientId", 100,
                "items", List.of(
                        Map.of("rxnormCode", "308191", "drugName", "Amoxicillin"),
                        Map.of("rxnormCode", "5640", "drugName", "Ibuprofen")
                )
        ));
        MvcResult result = mockMvc.perform(post("/api/v1/cds/check")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertNotNull(node.get("data").get("passed"));
        assertTrue(node.get("data").get("warnings").isArray());
    }

    // ──────────────────────────────────────────────────────
    // ──────────────────────────────────────────────────────

    @Test
    void pharmacies_shouldReturnDirectory() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/pharmacies")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(node.get("data").isArray());
        assertTrue(node.get("data").size() >= 3, "Seed data should have 5 pharmacies");
    }

    @Test
    void pharmacies_byState_shouldFilter() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/pharmacies")
                        .param("state", "IL")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(node.get("data").size() > 0);
    }

    // ──────────────────────────────────────────────────────
    // ──────────────────────────────────────────────────────

    @Test
    void prescriptionTransmit_shouldGenerateNcpdp() throws Exception {
        // 301 is active in the fixture (300 is completed). Non-controlled →
        // generates a draft NCPDP XML; no longer claims "transmitted"
        // (Review III C4).
        MvcResult result = mockMvc.perform(put("/api/v1/prescriptions/301/transmit")
                        .param("pharmacyId", "1")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertEquals("generated", node.get("data").get("status").asText());
    }

    // ──────────────────────────────────────────────────────
}
