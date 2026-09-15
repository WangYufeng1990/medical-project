package com.example.medical;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** System module: user, role and menu CRUD plus the AES key audit trail.
 * <p>
 * The tests here form a deliberate sequence (create → update → delete, or a claim
 * lifecycle), so this class keeps an explicit order. Ordering is class-local: the
 * class still runs on its own because cleanup-test-data.sql restores the seeded
 * state before it starts.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SystemIntegrationTest extends IntegrationTestSupport {

    private String adminToken, doctorToken, patientToken = null;

    @BeforeAll
    void authenticate() throws Exception {
        adminToken = login("admin", "admin123");
        doctorToken = login("doctor1", "doctor123");
        patientToken = patientLogin("patient1", "patient123");
    }

    // ──────────────────────────────────────────────────────

    @Test
    @Order(10)
    void userPage_asAdmin_shouldReturnPaginatedUsers() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/users")
                        .param("page", "1").param("size", "10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").get("total").asInt() >= 2);
        assertTrue(node.get("data").get("records").isArray());
    }

    @Test
    @Order(11)
    void userPage_asDoctor_shouldBeDenied() throws Exception {
        // Doctor should not access admin-only users endpoint
        MvcResult result = mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + doctorToken))
                .andReturn();
        assertNotEquals(200, result.getResponse().getStatus(),
                "Doctor should not access admin-only users endpoint");
    }

    @Test
    @Order(12)
    void getUserById_shouldReturnUser() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/users/1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertEquals("admin", node.get("data").get("username").asText());
        assertTrue(node.get("data").get("roles").isArray());
    }

    @Test
    @Order(13)
    void getUserById_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/users/9999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(14)
    void createUser_shouldSucceed() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "doctor2",
                "password", "Doctor@123",
                "realName", "Dr. Test User",
                "phone", "312-555-0099",
                "email", "test.doctor@medical.com",
                "gender", 1,
                "status", 1
        ));
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(15)
    void createUser_duplicateUsername_shouldReturn409() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "admin",
                "password", "Admin@123",
                "realName", "Duplicate",
                "status", 1
        ));
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(16)
    void updateUser_sameUsername_shouldSucceed() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "doctor2",
                "password", "NewPass@123",
                "realName", "Dr. Updated Name",
                "phone", "312-555-0088",
                "email", "updated@medical.com",
                "gender", 2,
                "status", 1
        ));
        mockMvc.perform(put("/api/v1/users/3")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(17)
    void updateUser_withBlankPassword_shouldFailValidation() throws Exception {
        // Backend requires @NotBlank password even on update — this is a known issue
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "doctor2",
                "password", "",
                "realName", "Updated"
        ));
        mockMvc.perform(put("/api/v1/users/3")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(18)
    void deleteUser_shouldSoftDelete() throws Exception {
        mockMvc.perform(delete("/api/v1/users/3")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(19)
    void userSearch_byKeyword_shouldFilter() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/users")
                        .param("keyword", "admin")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(node.get("data").get("total").asInt() >= 1);
    }

    // ──────────────────────────────────────────────────────
    // ──────────────────────────────────────────────────────

    @Test
    @Order(20)
    void rolePage_shouldReturnPaginatedRoles() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/roles")
                        .param("page", "1").param("size", "10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").get("total").asInt() >= 3);
    }

    @Test
    @Order(21)
    void createRole_shouldSucceed() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "roleName", "Nurse",
                "roleCode", "NURSE",
                "description", "Nurse role",
                "status", 1
        ));
        mockMvc.perform(post("/api/v1/roles")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(22)
    void createRole_duplicateCode_shouldReturn409() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "roleName", "Dup",
                "roleCode", "ADMIN",
                "description", "Dup role",
                "status", 1
        ));
        mockMvc.perform(post("/api/v1/roles")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(23)
    void deleteRole_shouldSucceed() throws Exception {
        mockMvc.perform(delete("/api/v1/roles/4")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(24)
    void roleEndpoints_asPatient_shouldBeDenied() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + patientToken))
                .andReturn();
        assertNotEquals(200, result.getResponse().getStatus(),
                "Patient should not access admin-only roles endpoint");
    }

    // ──────────────────────────────────────────────────────
    // ──────────────────────────────────────────────────────

    @Test
    @Order(25)
    void menuTree_shouldReturnTree() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/menus/tree")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").isArray());
        // Root menus: Dashboard, System, Patients, Appointments, Prescriptions, Billing
        assertTrue(node.get("data").size() >= 4);
    }

    @Test
    @Order(26)
    void menuList_shouldReturnFlatList() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/menus")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").isArray());
    }

    @Test
    @Order(27)
    void createMenu_shouldSucceed() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "parentId", 0,
                "menuName", "Reports",
                "path", "/reports",
                "component", "reports/index",
                "icon", "DataAnalysis",
                "type", "MENU",
                "permission", "report:list",
                "sort", 60,
                "status", 1
        ));
        mockMvc.perform(post("/api/v1/menus")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(28)
    void updateMenu_shouldSucceed() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "parentId", 0,
                "menuName", "Reports V2",
                "path", "/reports",
                "component", "reports/index",
                "icon", "DataAnalysis",
                "type", "MENU",
                "permission", "report:list",
                "sort", 61,
                "status", 1
        ));
        mockMvc.perform(put("/api/v1/menus/14")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(29)
    void deleteMenu_withNoChildren_shouldSucceed() throws Exception {
        mockMvc.perform(delete("/api/v1/menus/14")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(30)
    void deleteMenu_withChildren_shouldReturn409() throws Exception {
        // System menu (id=2) has children (Users, Roles, Menus)
        mockMvc.perform(delete("/api/v1/menus/2")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    // ──────────────────────────────────────────────────────
    // ──────────────────────────────────────────────────────

    @Test
    @Order(109)
    void keyAudit_shouldReturnHistory() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/keys/history")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(node.get("data").isArray());
        // @PostConstruct ordering may vary; array may be empty or have entries
    }

    // ──────────────────────────────────────────────────────
}
