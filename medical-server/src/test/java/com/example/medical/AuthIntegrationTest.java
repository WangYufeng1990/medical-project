package com.example.medical;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import java.time.LocalDateTime;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Authentication and session endpoints (login, refresh, logout, lockout). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthIntegrationTest extends IntegrationTestSupport {

    private String adminToken, doctorToken, patientToken = null;

    /** The lockout lives in patient_auth; asserting it needs the row, not an endpoint. */
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeAll
    void authenticate() throws Exception {
        adminToken = login("admin", "admin123");
        doctorToken = login("doctor1", "doctor123");
        patientToken = patientLogin("patient1", "patient123");
    }

    // ──────────────────────────────────────────────────────

    @Test
    void adminLogin_shouldSucceed() throws Exception {
        adminToken = login("admin", "admin123");
        assertNotNull(adminToken);
    }

    @Test
    void doctorLogin_shouldSucceed() throws Exception {
        doctorToken = login("doctor1", "doctor123");
        assertNotNull(doctorToken);
    }

    @Test
    void patientLogin_shouldSucceed() throws Exception {
        patientToken = patientLogin("patient1", "patient123");
        assertNotNull(patientToken);
    }

    @Test
    void login_withWrongPassword_shouldReturn401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", "admin", "password", "wrong"));
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_withNonexistentUser_shouldReturn401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", "nonexistent", "password", "x"));
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedAccess_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patientLogin_withWrongPassword_shouldReturn401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", "patient1", "password", "wrong"));
        mockMvc.perform(post("/api/v1/patient/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Failed patient logins must be counted, and the count must survive the 401 that
     * ends the attempt (R53-05). The increment joined the login's own transaction, so
     * the exception that answers 401 rolled it back: the counter stayed 0, no lock
     * ever engaged, and patient logins could be brute-forced freely.
     * <p>
     * Patient 2 is the probe because no other test signs in as that account, and its
     * row is restored afterwards either way.
     */
    @Test
    void patientLockout_shouldCountFailuresAndResetOnSuccess() throws Exception {
        try {
            for (int attempt = 1; attempt <= 5; attempt++) {
                loginPatientExpecting("patient2", "wrong-password", 401);
            }

            assertEquals(5, failedAttempts("patient2"),
                    "five failed attempts must be persisted, not rolled back with the 401");
            assertNotNull(lockedUntil("patient2"), "the fifth failure locks the account");

            loginPatientExpecting("patient2", "patient123", 403);
            assertEquals(5, failedAttempts("patient2"),
                    "a refused attempt while locked does not count another failure");

            // The lock expires: backdate it, then a real login must clear the counter.
            jdbcTemplate.update("UPDATE patient_auth SET locked_until = ? WHERE username = 'patient2'",
                    LocalDateTime.now().minusMinutes(1));
            loginPatientExpecting("patient2", "patient123", 200);

            assertEquals(0, failedAttempts("patient2"),
                    "a successful login must clear the counter for good — the reset is a bulk "
                            + "update, and saving the stale entity back over it would undo it");
            assertNull(lockedUntil("patient2"));
        } finally {
            resetPatientAuth("patient2");
        }
    }

    /**
     * The other half of R53-05: the reset runs as a bulk update, and the controller
     * used to save the entity it had read <em>before</em> that update — writing the
     * stale counter straight back over the reset. The count is planted here so the
     * merge is observable: with the counter already high, a successful login must
     * still clear it.
     */
    @Test
    void patientLogin_shouldClearAPreExistingFailureCount() throws Exception {
        try {
            jdbcTemplate.update("UPDATE patient_auth SET failed_attempts = 4, locked_until = NULL "
                    + "WHERE username = 'patient2'");

            loginPatientExpecting("patient2", "patient123", 200);

            assertEquals(0, failedAttempts("patient2"),
                    "saving the entity read before the bulk reset would write its stale count back");
        } finally {
            resetPatientAuth("patient2");
        }
    }

    private void loginPatientExpecting(String username, String password, int status) throws Exception {
        mockMvc.perform(post("/api/v1/patient/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", password))))
                .andExpect(status().is(status));
    }

    private int failedAttempts(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT failed_attempts FROM patient_auth WHERE username = ?", Integer.class, username);
    }

    private LocalDateTime lockedUntil(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT locked_until FROM patient_auth WHERE username = ?", LocalDateTime.class, username);
    }

    private void resetPatientAuth(String username) {
        jdbcTemplate.update("UPDATE patient_auth SET failed_attempts = 0, locked_until = NULL "
                + "WHERE username = ?", username);
    }

    @Test
    void refresh_inDevMode_shouldReturn401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("refreshToken", "some-token"));
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_shouldReturn200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    /**
     * Disabling an account must invalidate tokens already handed out: the check
     * runs inside the JWT claim mapper, which reads the account's
     * {@code forceLogoutAfter}. M8.5 inverted that read behind
     * {@code AccountRevocationCheck} and this was the branch's first test.
     * <p>
     * The probe account is created without roles, so the healthy answer for it is
     * 403 (authenticated, not authorized) — which is exactly what makes the last
     * assertion meaningful: 401 there can only come from authentication failing.
     */
    @Test
    void tokenIssuedBeforeAccountDisabled_shouldBeRejected() throws Exception {
        String username = "revocation-probe";
        String password = "Revoked@123";
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password,
                                "realName", "Revocation Probe",
                                "status", 1)))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        String probeToken = login(username, password);
        mockMvc.perform(get("/api/v1/users/doctors")
                        .header("Authorization", "Bearer " + probeToken))
                .andExpect(status().isForbidden());

        Long probeId = null;
        var page = mockMvc.perform(get("/api/v1/users")
                        .param("page", "1").param("size", "200")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn();
        for (var node : objectMapper.readTree(page.getResponse().getContentAsString())
                .get("data").get("records")) {
            if (username.equals(node.get("username").asText())) probeId = node.get("id").asLong();
        }
        assertNotNull(probeId, "created user not found");

        mockMvc.perform(put("/api/v1/users/" + probeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", 0)))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/doctors")
                        .header("Authorization", "Bearer " + probeToken))
                .andExpect(status().isUnauthorized());
    }

    // ──────────────────────────────────────────────────────
}
