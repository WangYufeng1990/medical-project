package com.example.medical;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MvcResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** FHIR R4 patient, case and observation endpoints. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FhirIntegrationTest extends IntegrationTestSupport {

    private String doctorToken = null;

    @BeforeAll
    void authenticate() throws Exception {
        doctorToken = login("doctor1", "doctor123");
    }

    // ──────────────────────────────────────────────────────

    @Test
    void fhirMetadata_shouldReturnCapabilityStatement() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/fhir/metadata"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/fhir+json"))
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("CapabilityStatement", node.get("resourceType").asText());
    }

    @Test
    void patientCase_shouldReturnFhirBundle() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/patients/100/case")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/fhir+json"))
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("Bundle", node.get("resourceType").asText());
    }

    // ──────────────────────────────────────────────────────
    // ──────────────────────────────────────────────────────

    @Test
    void fhirPatientById_shouldReturnFhirPatient() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/fhir/Patient/100")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/fhir+json"))
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("Patient", node.get("resourceType").asText());
    }

    @Test
    void fhirPatientSearch_shouldReturnBundle() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/fhir/Patient")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/fhir+json"))
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("Bundle", node.get("resourceType").asText());
    }

    @Test
    void fhirObservationSearch_shouldReturnBundle() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/fhir/Observation")
                        .param("patient", "100")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/fhir+json"))
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("Bundle", node.get("resourceType").asText());
        assertTrue(node.get("total").asInt() >= 0);
    }

    @Test
    void fhirObservationSearch_noPatient_shouldReturnBundle() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/fhir/Observation")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("Bundle", node.get("resourceType").asText());
    }

    /**
     * R53-09: the serialiser built the effective time as
     * {@code effectiveDate.toString() + ":00"}, which is only valid while the
     * value has no seconds — {@code LocalDateTime.toString()} omits them when
     * they are zero. A result collected at 08:00:30 became
     * {@code 2026-06-04T08:00:30:00} and the request failed.
     */
    @Test
    void fhirObservation_withNonZeroSeconds_shouldSerialiseEffectiveTime() throws Exception {
        jdbcTemplate.update("INSERT INTO observation "
                + "(patient_id, loinc_code, loinc_display, obs_value, unit, status, "
                + "source_message_id, effective_date) "
                + "VALUES (100, 'R53-09', 'Probe result', '1', 'mg', 'final', 'r53-09', "
                + "'2026-06-04 08:00:30')");
        try {
            MvcResult result = mockMvc.perform(get("/api/v1/fhir/Observation")
                            .param("patient", "100")
                            .param("code", "R53-09")
                            .header("Authorization", "Bearer " + doctorToken))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            assertEquals(1, node.get("entry").size(), "the probe observation must be in the bundle");
            JsonNode observation = node.get("entry").get(0).get("resource");
            assertEquals("2026-06-04T08:00:30",
                    observation.get("effectiveDateTime").asText(),
                    "seconds must be serialised once, not appended to an existing second");
        } finally {
            jdbcTemplate.update("DELETE FROM observation WHERE source_message_id = 'r53-09'");
        }
    }

    /**
     * The precision contract, on the detail endpoint too: minute-only input keeps
     * the {@code :00} the serialiser always produced, and a fractional second is
     * carried at millisecond precision instead of being glued onto the string.
     * The resource carries no offset because {@code effective_date} is stored as
     * wall-clock time — claiming one would invent a time zone the row never had.
     */
    @Test
    void fhirObservationDetail_shouldSerialiseEveryPrecisionToOneWellFormedTime() throws Exception {
        String[][] cases = {
                {"R53-09-M", "2026-06-04 08:00:00", "2026-06-04T08:00:00"},
                {"R53-09-S", "2026-06-04 08:00:30", "2026-06-04T08:00:30"},
                {"R53-09-F", "2026-06-04 08:00:30.5", "2026-06-04T08:00:30.500"},
        };
        try {
            for (String[] c : cases) {
                jdbcTemplate.update("INSERT INTO observation "
                        + "(patient_id, loinc_code, loinc_display, obs_value, unit, status, "
                        + "source_message_id, effective_date) "
                        + "VALUES (100, ?, 'Probe result', '1', 'mg', 'final', 'r53-09', ?)",
                        c[0], c[1]);
                Long id = jdbcTemplate.queryForObject(
                        "SELECT id FROM observation WHERE loinc_code = ?", Long.class, c[0]);

                MvcResult result = mockMvc.perform(get("/api/v1/fhir/Observation/" + id)
                                .header("Authorization", "Bearer " + doctorToken))
                        .andExpect(status().isOk())
                        .andReturn();
                JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
                assertEquals(c[2], node.get("effectiveDateTime").asText(),
                        c[0] + " must serialise one well-formed time");
            }
        } finally {
            jdbcTemplate.update("DELETE FROM observation WHERE source_message_id = 'r53-09'");
        }
    }

    // ──────────────────────────────────────────────────────
}
