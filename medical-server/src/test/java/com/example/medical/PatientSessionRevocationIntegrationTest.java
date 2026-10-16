package com.example.medical;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Patient sessions must not outlive the credentials they were issued for (R53-01).
 * <p>
 * A patient token carries a {@code patient} id in {@code uid}, which is deliberately
 * never checked against {@code sys_user} revocation (the two tables' ids overlap),
 * and nothing checked it anywhere else: the access token kept answering 200 after a
 * password change or an account disablement, and the refresh token kept minting
 * replacements for its full 30-day life. Tokens now carry the credential marker they
 * were issued against, and the resource server compares it with the account on every
 * request.
 * <p>
 * The probes are patient2 and patient3 — no other test signs in as either — and each
 * of them is restored from a snapshot before every case, with credentials and status
 * put back afterwards.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PatientSessionRevocationIntegrationTest extends IntegrationTestSupport {

    private static final String PROBE = "patient2";
    private static final String OTHER = "patient3";
    private static final String CURRENT_PASSWORD = "patient123";
    /** Satisfies the policy: 8+ characters with upper, lower, digit and special. */
    private static final String NEW_PASSWORD = "Probe1234!";

    private static final String COLUMNS =
            "id, patient_id, username, password, status, last_login_time, failed_attempts, "
                    + "locked_until, password_changed_at, create_time, update_time, is_deleted, version";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Map<String, Map<String, Object>> snapshots = new java.util.HashMap<>();

    @BeforeAll
    void snapshotProbeAccounts() {
        for (String username : new String[]{PROBE, OTHER}) {
            snapshots.put(username, jdbcTemplate.queryForMap(
                    "SELECT * FROM patient_auth WHERE username = ?", username));
        }
    }

    @BeforeEach
    void restoreProbeAccounts() {
        snapshots.keySet().forEach(this::restoreAccount);
    }

    @AfterAll
    void restoreProbeAccountsAfterwards() {
        snapshots.keySet().forEach(this::restoreAccount);
    }

    // ──────────────────────────────────────────────────────

    @Test
    void patientToken_shouldWorkForAccessAndRefresh() throws Exception {
        String[] tokens = loginTokens(PROBE, CURRENT_PASSWORD);

        me(tokens[0], 200);
        refresh(tokens[1], 200);
    }

    @Test
    void afterAPasswordChange_theOldAccessToken_shouldBeRejected() throws Exception {
        String[] tokens = loginTokens(PROBE, CURRENT_PASSWORD);
        me(tokens[0], 200);

        changePassword(tokens[0], CURRENT_PASSWORD, NEW_PASSWORD);

        me(tokens[0], 401);
    }

    @Test
    void afterAPasswordChange_theOldRefreshToken_shouldBeRejected() throws Exception {
        String[] tokens = loginTokens(PROBE, CURRENT_PASSWORD);

        changePassword(tokens[0], CURRENT_PASSWORD, NEW_PASSWORD);

        refresh(tokens[1], 401);
    }

    @Test
    void afterAPasswordChange_theNewPassword_shouldSignIn() throws Exception {
        String[] tokens = loginTokens(PROBE, CURRENT_PASSWORD);
        changePassword(tokens[0], CURRENT_PASSWORD, NEW_PASSWORD);

        String[] fresh = loginTokens(PROBE, NEW_PASSWORD);

        me(fresh[0], 200);
        refresh(fresh[1], 200);
    }

    @Test
    void aDisabledAccount_cannotAccessOrRefresh() throws Exception {
        String[] tokens = loginTokens(PROBE, CURRENT_PASSWORD);

        jdbcTemplate.update("UPDATE patient_auth SET status = 0 WHERE username = ?", PROBE);

        me(tokens[0], 401);
        refresh(tokens[1], 401);
    }

    @Test
    void aRemovedAccount_cannotAccessOrRefresh() throws Exception {
        String[] tokens = loginTokens(PROBE, CURRENT_PASSWORD);

        jdbcTemplate.update("DELETE FROM patient_auth WHERE username = ?", PROBE);

        me(tokens[0], 401);
        refresh(tokens[1], 401);
    }

    /** The marker belongs to one credential: another patient's change is none of this session's business. */
    @Test
    void aPasswordChangeOnAnotherAccount_shouldNotAffectThisOne() throws Exception {
        String[] mine = loginTokens(PROBE, CURRENT_PASSWORD);
        String[] theirs = loginTokens(OTHER, CURRENT_PASSWORD);

        changePassword(theirs[0], CURRENT_PASSWORD, NEW_PASSWORD);

        me(mine[0], 200);
        refresh(mine[1], 200);
    }

    // ──────────────────────────────────────────────────────

    private String[] loginTokens(String username, String password) throws Exception {
        String data = objectMapper.readTree(mockMvc.perform(post("/api/v1/patient/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("data").toString();
        var node = objectMapper.readTree(data);
        return new String[]{node.get("token").asText(), node.get("refreshToken").asText()};
    }

    private void me(String token, int expectedStatus) throws Exception {
        mockMvc.perform(get("/api/v1/patient/me").header("Authorization", "Bearer " + token))
                .andExpect(status().is(expectedStatus));
    }

    private void refresh(String refreshToken, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/patient/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().is(expectedStatus));
    }

    private void changePassword(String accessToken, String oldPassword, String newPassword) throws Exception {
        mockMvc.perform(put("/api/v1/patient/me/password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "oldPassword", oldPassword, "newPassword", newPassword))))
                .andExpect(status().isOk());
    }

    /** Puts one probe account back exactly as it was seeded, row and all. */
    private void restoreAccount(String username) {
        Map<String, Object> row = snapshots.get(username);
        jdbcTemplate.update("DELETE FROM patient_auth WHERE username = ?", username);
        jdbcTemplate.update("INSERT INTO patient_auth (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                row.get("id"), row.get("patient_id"), row.get("username"), row.get("password"),
                row.get("status"), row.get("last_login_time"), row.get("failed_attempts"),
                row.get("locked_until"), row.get("password_changed_at"), row.get("create_time"),
                row.get("update_time"), row.get("is_deleted"), row.get("version"));
    }
}
