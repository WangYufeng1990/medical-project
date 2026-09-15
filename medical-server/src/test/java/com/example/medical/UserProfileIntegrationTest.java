package com.example.medical;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Staff self-service profile endpoint. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserProfileIntegrationTest extends IntegrationTestSupport {

    private String adminToken = null;

    @BeforeAll
    void authenticate() throws Exception {
        adminToken = login("admin", "admin123");
    }

    // ──────────────────────────────────────────────────────

    @Test
    void getProfile_shouldReturnCurrentUser() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertEquals("admin", node.get("data").get("username").asText());
    }

    @Test
    void updateProfile_shouldSucceed() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "realName", "Admin Updated",
                "phone", "312-555-0001",
                "email", "admin.updated@medical.com",
                "gender", 1
        ));
        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_shouldSucceed() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "oldPassword", "admin123",
                "newPassword", "Admin@456"
        ));
        mockMvc.perform(put("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Change back for other tests
        String revertBody = objectMapper.writeValueAsString(Map.of(
                "oldPassword", "Admin@456",
                "newPassword", "Admin@123"
        ));
        mockMvc.perform(put("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON).content(revertBody)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_wrongOldPassword_shouldReturn400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "oldPassword", "wrongpassword",
                "newPassword", "NewPass@123"
        ));
        mockMvc.perform(put("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    // ──────────────────────────────────────────────────────
}
