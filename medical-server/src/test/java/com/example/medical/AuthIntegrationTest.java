package com.example.medical;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Authentication and session endpoints (login, refresh, logout, lockout). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthIntegrationTest extends IntegrationTestSupport {

    private String adminToken, doctorToken, patientToken = null;

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
