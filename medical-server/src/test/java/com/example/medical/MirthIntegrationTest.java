package com.example.medical;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
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

    // ──────────────────────────────────────────────────────
}
