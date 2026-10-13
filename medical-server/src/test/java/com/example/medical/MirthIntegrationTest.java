package com.example.medical;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Mirth Connect ADT and lab result ingestion. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MirthIntegrationTest extends IntegrationTestSupport {

    private String adminToken = null;

    @BeforeAll
    void authenticate() throws Exception {
        adminToken = login("admin", "admin123");
    }

    // ──────────────────────────────────────────────────────

    @Test
    void integrationAdt_shouldProcessEvent() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "sourceMessageId", "test-adt-" + System.currentTimeMillis(),
                "eventType", "A08",
                "patient", Map.of("mrn", "MRN-10001", "name", "James Anderson",
                        "dateOfBirth", "1998-02-14", "sexAtBirth", "M"),
                "visit", Map.of("visitNumber", "V-TEST", "department", "Cardiology")
        ));
        mockMvc.perform(post("/api/v1/integration/adt")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Integration-Key", "dev-integration-key"))
                .andExpect(status().isOk());
    }

    @Test
    void integrationLabResults_shouldSaveObservations() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "sourceMessageId", "test-lab-" + System.currentTimeMillis(),
                "patientMrn", "MRN-10001",
                "orderCode", "CBC",
                "collectionDate", "2026-06-01T08:00:00",
                "results", List.of(
                        Map.of("loincCode", "6690-2", "display", "WBC",
                                "value", "7.5", "unit", "10*3/uL",
                                "referenceRange", "4.0-11.0", "abnormalFlag", "N")
                )
        ));
        mockMvc.perform(post("/api/v1/integration/lab-results")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Integration-Key", "dev-integration-key"))
                .andExpect(status().isOk());
    }

    /**
     * HL7 sends two-character critical flags (HH/LL) while the column is
     * {@code CHAR(1)}: before normalisation the whole message failed with a 500
     * "Value too long for column ABNORMAL_FLAG". The intake now folds them down,
     * so a real lab feed cannot take the ingest endpoint out.
     */
    @Test
    void integrationLabResults_shouldAcceptTwoCharacterCriticalFlags() throws Exception {
        for (String flag : List.of("HH", "LL", "hu", "LU")) {
            String body = objectMapper.writeValueAsString(Map.of(
                    "sourceMessageId", "test-critical-" + flag + "-" + System.currentTimeMillis(),
                    "patientMrn", "MRN-10001",
                    "results", List.of(Map.of(
                            "loincCode", "2345-7", "display", "Glucose",
                            "value", "220", "unit", "mg/dL",
                            "referenceRange", "70-99", "abnormalFlag", flag))));
            mockMvc.perform(post("/api/v1/integration/lab-results")
                            .contentType(MediaType.APPLICATION_JSON).content(body)
                            .header("Authorization", "Bearer " + adminToken)
                            .header("X-Integration-Key", "dev-integration-key"))
                    .andExpect(status().isOk());
        }
    }

    /** The ACK an interface engine parses: the count key is absent, not null (52.5). */
    @Test
    void integrationAdt_shouldAckTheSourceMessageIdWithoutACount() throws Exception {
        String messageId = "test-ack-adt-" + System.currentTimeMillis();
        String body = objectMapper.writeValueAsString(Map.of(
                "sourceMessageId", messageId, "eventType", "A08",
                "patient", Map.of("mrn", "MRN-10001", "name", "James Anderson")));

        JsonNode ack = objectMapper.readTree(mockMvc.perform(post("/api/v1/integration/adt")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Integration-Key", "dev-integration-key"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("data");

        assertEquals("ACK", ack.get("status").asText());
        assertEquals(messageId, ack.get("sourceMessageId").asText());
        assertFalse(ack.has("recordsCreated"), "an ADT ack carries no count");
    }

    /**
     * The ADT message is a PHI write, so it is the patient module's write and the
     * patient module's audit row (52.5): created when the MRN is new, updated when
     * it is not, and never duplicated.
     */
    @Test
    void integrationAdt_shouldUpsertThePatientUnderThePatientModuleAndAuditIt() throws Exception {
        String mrn = "MRN-TEST-525";
        int createsBefore = auditTotal("patient", "CREATE", null);
        int updatesBefore = auditTotal("patient", "UPDATE", null);

        adt("test-adt-create-" + System.currentTimeMillis(), mrn, "Evanston");

        JsonNode afterCreate = patientsByMrn(mrn);
        assertEquals(1, afterCreate.size(), "the ADT message should create one patient");
        assertEquals("Test R52.5 Probe", afterCreate.get(0).get("name").asText());
        assertEquals("Evanston", afterCreate.get(0).get("city").asText());
        assertEquals("IL", afterCreate.get(0).get("state").asText());
        assertEquals(createsBefore + 1, awaitAudit("patient", "CREATE", null, createsBefore + 1),
                "creating a patient from an ADT message must be audited as a patient write");

        adt("test-adt-update-" + System.currentTimeMillis(), mrn, "Chicago");

        JsonNode afterUpdate = patientsByMrn(mrn);
        assertEquals(1, afterUpdate.size(), "a known MRN is updated, not duplicated");
        assertEquals("Chicago", afterUpdate.get(0).get("city").asText());
        assertEquals("IL", afterUpdate.get(0).get("state").asText(),
                "a field the message does not carry is left as it was, not cleared");
        assertEquals(updatesBefore + 1, awaitAudit("patient", "UPDATE", null, updatesBefore + 1),
                "updating a patient from an ADT message must be audited as a patient write");
    }

    @Test
    void integrationLabResults_shouldAckTheCountAndIgnoreADuplicateMessage() throws Exception {
        String messageId = "test-lab-count-" + System.currentTimeMillis();
        String body = objectMapper.writeValueAsString(Map.of(
                "sourceMessageId", messageId, "patientMrn", "MRN-10001",
                "collectionDate", "2026-06-02T08:00:00",
                "results", List.of(
                        Map.of("loincCode", "6690-2", "display", "WBC", "value", "7.5",
                                "unit", "10*3/uL", "referenceRange", "4.0-11.0", "abnormalFlag", "N"),
                        Map.of("loincCode", "718-7", "display", "Hemoglobin", "value", "13.1",
                                "unit", "g/dL", "referenceRange", "13.0-17.0", "abnormalFlag", "N"))));

        assertEquals(2, labAck(body).get("recordsCreated").asInt(),
                "a panel arrives as one message with many results, and stores one row per result");
        assertEquals(0, labAck(body).get("recordsCreated").asInt(),
                "the same sourceMessageId is ignored the second time");
    }

    /** An unmapped MRN is an interface problem, not an error: 200 with a zero count. */
    @Test
    void integrationLabResults_forAnUnknownMrn_shouldAckZero() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "sourceMessageId", "test-lab-unknown-" + System.currentTimeMillis(),
                "patientMrn", "MRN-DOES-NOT-EXIST",
                "results", List.of(Map.of("loincCode", "6690-2", "display", "WBC", "value", "7.5"))));

        assertEquals(0, labAck(body).get("recordsCreated").asInt());
    }

    /** Storing observations is a write into the patient module, so it is audited there. */
    @Test
    void integrationLabResults_shouldAuditTheIngestUnderThePatientModule() throws Exception {
        long patientId = 100;
        int before = auditTotal("patient", "LAB_INGEST", patientId);

        String body = objectMapper.writeValueAsString(Map.of(
                "sourceMessageId", "test-lab-audit-" + System.currentTimeMillis(),
                "patientMrn", "MRN-10001",
                "results", List.of(Map.of("loincCode", "6690-2", "display", "WBC", "value", "7.5"))));

        assertEquals(1, labAck(body).get("recordsCreated").asInt());
        assertEquals(before + 1, awaitAudit("patient", "LAB_INGEST", patientId, before + 1),
                "the stored observations must be attributed to the patient they belong to");
    }

    // ──────────────────────────────────────────────────────

    private void adt(String messageId, String mrn, String city) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "sourceMessageId", messageId, "eventType", "A08",
                "patient", Map.of("mrn", mrn, "name", "Test R52.5 Probe",
                        "dateOfBirth", "1975-04-02", "sexAtBirth", "F",
                        // The wire key is "state"; the field behind it is stateCode (@JsonProperty).
                        "address", Map.of("line1", "100 Probe St", "city", city,
                                "state", "IL", "zip", "60201"))));
        mockMvc.perform(post("/api/v1/integration/adt")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Integration-Key", "dev-integration-key"))
                .andExpect(status().isOk());
    }

    private JsonNode labAck(String body) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/integration/lab-results")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Integration-Key", "dev-integration-key"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("data");
    }

    private JsonNode patientsByMrn(String mrn) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/v1/patients")
                        .param("keyword", mrn)
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString()).get("data").get("records");
    }

    private int auditTotal(String module, String action, Long patientId) throws Exception {
        var request = get("/api/v1/audit-logs")
                .param("module", module).param("action", action).param("size", "1");
        if (patientId != null) request = request.param("patientId", String.valueOf(patientId));
        return objectMapper.readTree(mockMvc.perform(request
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString()).get("data").get("total").asInt();
    }

    /** Audit rows are written asynchronously — poll briefly rather than assert instantly. */
    private int awaitAudit(String module, String action, Long patientId, int expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        int total = auditTotal(module, action, patientId);
        while (total < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
            total = auditTotal(module, action, patientId);
        }
        return total;
    }

    // ──────────────────────────────────────────────────────
}
