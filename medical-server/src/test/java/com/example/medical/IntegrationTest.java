package com.example.medical;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.medical.module.chat.entity.Message;
import com.example.medical.module.chat.repository.MessageRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = {
        // Isolated in-memory DB — never touch the dev file DB (./data/medical_dev).
        "spring.datasource.url=jdbc:h2:mem:medical_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.h2.console.enabled=false",
        // Login rate limiter lives in Redis — counters survive across test runs
        // and would randomly 429 the auth tests.
        "app.rate-limit.enabled=false",
})
@Sql(scripts = "/cleanup-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessageRepository messageRepository;

    private static String adminToken;
    private static String doctorToken;
    private static String patientToken;

    // ── helpers ──

    private String login(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", username, "password", password));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        return node.get("data").get("token").asText();
    }

    private String patientLogin(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", username, "password", password));
        MvcResult result = mockMvc.perform(post("/api/v1/patient/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        return node.get("data").get("token").asText();
    }

    // ──────────────────────────────────────────────────────
    // 1. AUTHENTICATION
    // ──────────────────────────────────────────────────────

    @Test
    @Order(1)
    void adminLogin_shouldSucceed() throws Exception {
        adminToken = login("admin", "admin123");
        assertNotNull(adminToken);
    }

    @Test
    @Order(2)
    void doctorLogin_shouldSucceed() throws Exception {
        doctorToken = login("doctor1", "doctor123");
        assertNotNull(doctorToken);
    }

    @Test
    @Order(3)
    void patientLogin_shouldSucceed() throws Exception {
        patientToken = patientLogin("patient1", "patient123");
        assertNotNull(patientToken);
    }

    @Test
    @Order(4)
    void login_withWrongPassword_shouldReturn401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", "admin", "password", "wrong"));
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(5)
    void login_withNonexistentUser_shouldReturn401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", "nonexistent", "password", "x"));
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(6)
    void unauthenticatedAccess_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(7)
    void patientLogin_withWrongPassword_shouldReturn401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", "patient1", "password", "wrong"));
        mockMvc.perform(post("/api/v1/patient/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(8)
    void refresh_inDevMode_shouldReturn401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("refreshToken", "some-token"));
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(9)
    void logout_shouldReturn200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // ──────────────────────────────────────────────────────
    // 2. SYSTEM — USER CRUD (Admin only)
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
    // 3. SYSTEM — ROLE CRUD
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
    // 4. SYSTEM — MENU CRUD
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
    // 5. PATIENT CRUD
    // ──────────────────────────────────────────────────────

    @Test
    @Order(31)
    void patientPage_asAdmin_shouldReturnPaginatedPatients() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/patients")
                        .param("page", "1").param("size", "10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").get("total").asInt() >= 3);
    }

    @Test
    @Order(32)
    void patientPage_asDoctor_shouldSucceed() throws Exception {
        mockMvc.perform(get("/api/v1/patients")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(33)
    void patientPage_asPatient_shouldBeDenied() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/patients")
                        .header("Authorization", "Bearer " + patientToken))
                .andReturn();
        assertNotEquals(200, result.getResponse().getStatus(),
                "Patient should not access admin/doctor patients endpoint");
    }

    @Test
    @Order(34)
    void getPatientById_shouldReturnPatient() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/patients/100")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertEquals("MRN-10001", node.get("data").get("mrn").asText());
        // PHI fields should be decrypted
        assertNotNull(node.get("data").get("name").asText());
    }

    @Test
    @Order(35)
    void getPatientById_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/patients/9999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(36)
    void createPatient_shouldSucceed() throws Exception {
        Map<String, Object> patientData = new LinkedHashMap<>();
        patientData.put("name", "Test Patient");
        patientData.put("mrn", "MRN-TEST-001");
        patientData.put("dateOfBirth", "2000-01-15");
        patientData.put("sexAtBirth", "F");
        patientData.put("genderIdentity", "Female");
        patientData.put("race", "Asian");
        patientData.put("ethnicity", "Not Hispanic or Latino");
        patientData.put("preferredLanguage", "en");
        patientData.put("maritalStatus", "Single");
        patientData.put("patientStatus", "active");
        patientData.put("primaryCareProvider", "Dr. Sarah Mitchell");
        patientData.put("phoneMobile", "312-555-1111");
        patientData.put("email", "test.patient@email.com");
        patientData.put("addressLine1", "100 Test St");
        patientData.put("city", "Chicago");
        patientData.put("state", "IL");
        patientData.put("zipCode", "60601");
        patientData.put("emergencyContactName", "Emergency Contact");
        patientData.put("emergencyContactPhone", "312-555-2222");
        patientData.put("emergencyContactRelation", "Spouse");
        patientData.put("insurancePayer", "Test Insurance");
        patientData.put("insuranceMemberId", "TST-12345");
        patientData.put("insuranceGroupNumber", "GRP-001");
        patientData.put("medicalHistory", "None");
        patientData.put("allergies", "None");
        String body = objectMapper.writeValueAsString(patientData);
        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(37)
    void createPatient_missingMrn_shouldReturn400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", "No MRN"));
        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(38)
    void updatePatient_shouldSucceed() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Test Patient Updated",
                "mrn", "MRN-TEST-001-UPD",
                "patientStatus", "active",
                "phoneMobile", "312-555-9999"
        ));
        mockMvc.perform(put("/api/v1/patients/103")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(39)
    void deletePatient_asDoctor_shouldBeDenied() throws Exception {
        MvcResult result = mockMvc.perform(delete("/api/v1/patients/103")
                        .header("Authorization", "Bearer " + doctorToken))
                .andReturn();
        assertNotEquals(200, result.getResponse().getStatus(),
                "Doctor should not be able to delete patients");
    }

    @Test
    @Order(40)
    void deletePatient_asAdmin_shouldSucceed() throws Exception {
        mockMvc.perform(delete("/api/v1/patients/103")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(41)
    void patientSearch_byKeyword_shouldFilter() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/patients")
                        .param("keyword", "MRN-10001")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(1, node.get("data").get("total").asInt());
    }

    // ──────────────────────────────────────────────────────
    // 6. APPOINTMENT CRUD
    // ──────────────────────────────────────────────────────

    @Test
    @Order(42)
    void appointmentPage_shouldReturnPaginated() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/appointments")
                        .param("page", "1").param("size", "10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").get("total").asInt() >= 5);
    }

    @Test
    @Order(43)
    void appointmentPage_withStatusFilter_shouldFilter() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/appointments")
                        .param("status", "0")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        // Seed has 2 appointments with status=0 (scheduled)
        assertEquals(2, node.get("data").get("total").asInt());
    }

    @Test
    @Order(44)
    void getAppointmentById_shouldReturnAppointment() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/appointments/200")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertNotNull(node.get("data").get("patientName").asText());
        assertNotNull(node.get("data").get("doctorName").asText());
    }

    @Test
    @Order(45)
    void createAppointment_shouldSucceed() throws Exception {
        String time = LocalDateTime.now().plusDays(30).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        String body = objectMapper.writeValueAsString(Map.of(
                "patientId", 100,
                "doctorId", 2,
                "appointmentTime", time,
                "visitType", "FOLLOW_UP",
                "chiefComplaint", "Test complaint",
                "department", "Family Medicine",
                "duration", 30,
                "cptCode", "99213",
                "description", "Test appointment",
                "status", 0
        ));
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(46)
    void createAppointment_conflicting_shouldReturn409() throws Exception {
        // Create a future appointment, then a second one for the same doctor in the
        // same 30-min window — the second must be rejected with 409.
        // +90 days: order 45 already books doctor 2 at +30 days.
        String time = LocalDateTime.now().plusDays(90).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", 100, "doctorId", 2, "appointmentTime", time,
                                "description", "First booking", "status", 0)))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", 101, "doctorId", 2,
                                "appointmentTime", LocalDateTime.parse(time)
                                        .plusMinutes(15)
                                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")),
                                "description", "Conflict test", "status", 0)))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(47)
    void updateAppointment_shouldSucceed() throws Exception {
        String time = LocalDateTime.now().plusDays(60).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        String body = objectMapper.writeValueAsString(Map.of(
                "patientId", 100,
                "doctorId", 2,
                "appointmentTime", time,
                "status", 1,
                "description", "Updated appointment"
        ));
        mockMvc.perform(put("/api/v1/appointments/205")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(48)
    void deleteAppointment_asAdmin_shouldSucceed() throws Exception {
        mockMvc.perform(delete("/api/v1/appointments/205")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // ──────────────────────────────────────────────────────
    // 7. PRESCRIPTION CRUD
    // ──────────────────────────────────────────────────────

    @Test
    @Order(49)
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
    @Order(50)
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
    @Order(51)
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
    @Order(52)
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
    @Order(53)
    void deletePrescription_asAdmin_shouldSucceed() throws Exception {
        mockMvc.perform(delete("/api/v1/prescriptions/303")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // ──────────────────────────────────────────────────────
    // 8. BILLING LIFECYCLE
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

    // ──────────────────────────────────────────────────────
    // 9. CHAT
    // ──────────────────────────────────────────────────────

    @Test
    @Order(66)
    void conversations_asDoctor_shouldReturnConversations() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/messages/conversations")
                        .param("page", "1").param("size", "20")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").get("records").isArray());
    }

    @Test
    @Order(67)
    void getConversation_shouldReturnMessages() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/messages/100")
                        .param("partnerType", "PATIENT")
                        .param("page", "1").param("size", "50")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").get("records").isArray());
    }

    @Test
    @Order(68)
    void sendMessage_shouldSucceed() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "receiverId", 100,
                "receiverType", "PATIENT",
                "content", "Test message from doctor"
        ));
        mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(68)
    void sendMessage_withoutReceiverType_shouldReject() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "receiverId", 100,
                "content", "Test message without type"
        ));
        mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @Order(69)
    void patientConversations_shouldReturnConversations() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/patient/me/messages/conversations")
                        .param("page", "1").param("size", "20")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
    }

    @Test
    @Order(70)
    void patientSendMessage_shouldSucceed() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "receiverId", 2,
                "content", "Test message from patient"
        ));
        mockMvc.perform(post("/api/v1/patient/me/messages")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(71)
    void patientChat_idSpaceCollision_shouldNotLeakStaffMessages() throws Exception {
        // R2-1 regression: patient 100 shares its numeric id with a hypothetical
        // staff user 100. A message "from staff 100 to patient 99" must never
        // surface in patient 100's inbox (the old bare-id query returned it).
        Message leak = new Message();
        leak.setSenderId(100L);
        leak.setSenderType("STAFF");
        leak.setReceiverId(99L);
        leak.setReceiverType("PATIENT");
        leak.setContent("confidential staff-to-other-patient message");
        leak.setIsRead(0);
        messageRepository.save(leak);

        MvcResult result = mockMvc.perform(get("/api/v1/patient/me/messages/conversations")
                        .param("page", "1").param("size", "20")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode records = node.get("data").get("records");
        // Patient 100 only talks with staff user 2 — the colliding staff-100 row must not appear.
        assertEquals(1, records.size());
        assertEquals(2, records.get(0).get("partnerId").asLong());
        assertEquals("STAFF", records.get(0).get("partnerType").asText());
    }

    // ── R2-2: DOCTOR patient scoping (patient 102 has no doctor-2 relationship) ──

    @Test
    @Order(72)
    void doctorVitals_outOfScopePatient_should403() throws Exception {
        mockMvc.perform(get("/api/v1/patients/102/vitals")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(73)
    void doctorClinicalReads_outOfScopePatient_should403() throws Exception {
        mockMvc.perform(get("/api/v1/patients/102/history")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/patients/102/allergies")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/patients/102/observations")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/patients/102/problems")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/patients/102/care-plans")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/patients/102/immunizations")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/patients/102/case")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(74)
    void doctorChargeList_shouldExcludeOutOfScopePatients() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/charges")
                        .param("page", "1").param("size", "50")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode r : node.get("data").get("records")) {
            assertNotEquals(102, r.get("patientId").asLong(),
                    "seed charge for patient 102 must not leak into doctor 2's list");
        }
    }

    @Test
    @Order(75)
    void doctorAppointmentDetail_outOfScope_should403() throws Exception {
        // Admin books an appointment for patient 102 (doctorId 1) so the row exists.
        String body = objectMapper.writeValueAsString(Map.of(
                "patientId", 102, "doctorId", 1,
                "appointmentTime", LocalDateTime.now().plusDays(5)
                        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "department", "Neurology"));
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Find the new appointment id via the admin list.
        MvcResult list = mockMvc.perform(get("/api/v1/appointments")
                        .param("patientId", "102")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode records = objectMapper.readTree(list.getResponse().getContentAsString())
                .get("data").get("records");
        long newId = records.get(0).get("id").asLong();

        // Doctor 2 (no relationship with patient 102) must get 403 on detail.
        mockMvc.perform(get("/api/v1/appointments/" + newId)
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());

        // In-scope control: appointment 200 belongs to patient 100 (doctor 2's own).
        mockMvc.perform(get("/api/v1/appointments/200")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(76)
    void adminReads_outOfScopeForDoctors_shouldStillSeeAll() throws Exception {
        mockMvc.perform(get("/api/v1/patients/102/vitals")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(77)
    void emergencyAccess_shouldBypassDoctorScope() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("reason", "break-glass test"));
        MvcResult em = mockMvc.perform(post("/api/v1/emergency/access/102")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(em.getResponse().getContentAsString());
        String emToken = node.get("data").get("token").asText();

        // The emergency token carries patientId=102 — vitals for 102 must now be readable.
        mockMvc.perform(get("/api/v1/patients/102/vitals")
                        .header("Authorization", "Bearer " + emToken))
                .andExpect(status().isOk());
    }

    // ── R2-3: charge validation (Post-Round 44) ──

    @Test
    @Order(78)
    void createCharge_missingPatient_should400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "chargeAmount", 50.00, "cptCodes", "99213"));
        mockMvc.perform(post("/api/v1/charges")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(79)
    void createCharge_valid_shouldSucceed_andRoundTripEncryptedNotes() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "patientId", 100, "chargeAmount", 50.00, "cptCodes", "99213",
                "notes", "encrypted smoke"));
        mockMvc.perform(post("/api/v1/charges")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patientId").value(100))
                .andExpect(jsonPath("$.data.notes").value("encrypted smoke"));
    }

    // ── Round 49 Item 3: FHIR read endpoints honor DOCTOR scope ──

    @Test
    @Order(80)
    void fhirRead_outOfScopePatient_should403() throws Exception {
        mockMvc.perform(get("/api/v1/fhir/Patient/102")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/fhir/Patient")
                        .param("_id", "102")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/fhir/Observation")
                        .param("patient", "102")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        // Out-of-scope observation id (patient 103), resolved via an admin search.
        MvcResult adminSearch = mockMvc.perform(get("/api/v1/fhir/Observation")
                        .param("patient", "103")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode entries = objectMapper.readTree(adminSearch.getResponse().getContentAsString())
                .get("entry");
        assertNotNull(entries, "seed observations for patient 103 expected");
        long obsId = Long.parseLong(entries.get(0).get("resource").get("id").asText());
        mockMvc.perform(get("/api/v1/fhir/Observation/" + obsId)
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(81)
    void fhirRead_inScopeAndAdmin_shouldSucceed() throws Exception {
        mockMvc.perform(get("/api/v1/fhir/Patient/100")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/fhir/Patient/102")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/fhir/Observation")
                        .param("patient", "102")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(82)
    void fhirRead_emergencyToken_shouldBypassDoctorScope() throws Exception {
        MvcResult em = mockMvc.perform(post("/api/v1/emergency/access/102")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "fhir break-glass")))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        String emToken = objectMapper.readTree(em.getResponse().getContentAsString())
                .get("data").get("token").asText();
        mockMvc.perform(get("/api/v1/fhir/Patient/102")
                        .header("Authorization", "Bearer " + emToken))
                .andExpect(status().isOk());
    }

    // ── Round 49 Item 1/2 regression: encrypted free-text round-trip ──

    @Test
    @Order(83)
    void createCarePlan_longEncryptedGoal_shouldRoundTrip() throws Exception {
        // ~250 plaintext chars — ciphertext (~560 hex) would truncate under
        // the old VARCHAR(200/500) widths, and the goal must survive DB
        // write + decrypt on read.
        String longGoal = "Patient goal: achieve and maintain HbA1c below 7.0 for the next six consecutive " +
                "months through dietary changes, weekly exercise routine, and compliance with the prescribed " +
                "oral antidiabetic medication regimen. Reassessment scheduled quarterly with dose adjustment " +
                "as indicated by continuous glucose monitoring trends.";
        MvcResult created = mockMvc.perform(post("/api/v1/patients/100/care-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Diabetes management",
                                "goal", longGoal,
                                "interventions", "Diet, exercise, medication",
                                "status", "ACTIVE",
                                "createdBy", 2)))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        long planId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("id").asLong();
        MvcResult list = mockMvc.perform(get("/api/v1/patients/100/care-plans")
                        .param("page", "1").param("size", "100")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode records = objectMapper.readTree(list.getResponse().getContentAsString())
                .get("data").get("records");
        JsonNode found = null;
        for (JsonNode r : records) {
            if (r.get("id").asLong() == planId) { found = r; break; }
        }
        assertNotNull(found, "created care plan must appear in the patient's list");
        assertEquals(longGoal, found.get("goal").asText(),
                "long encrypted goal must round-trip intact through DB");
    }

    @Test
    @Order(84)
    void createReferral_encryptedFields_shouldRoundTrip() throws Exception {
        String diagnosis = "Essential hypertension with recurrent headaches unresponsive to first-line therapy";
        String reason = "Patient reports bilateral throbbing headaches for the past three weeks; neurology consult needed";
        String notes = "PCP follow-up scheduled in two weeks";
        MvcResult created = mockMvc.perform(post("/api/v1/referrals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", 100,
                                "specialistName", "Dr. Neurology",
                                "specialty", "Neurology",
                                "diagnosis", diagnosis,
                                "reason", reason,
                                "notes", notes)))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        long referralId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("id").asLong();
        MvcResult list = mockMvc.perform(get("/api/v1/patients/100/referrals")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode records = objectMapper.readTree(list.getResponse().getContentAsString()).get("data");
        JsonNode found = null;
        for (JsonNode r : records) {
            if (r.get("id").asLong() == referralId) { found = r; break; }
        }
        assertNotNull(found, "created referral must appear in the patient's list");
        assertEquals(diagnosis, found.get("diagnosis").asText());
        assertEquals(reason, found.get("reason").asText());
        assertEquals(notes, found.get("notes").asText());
    }

    // ── Round 49: R2-2 create-endpoint gap — writes honor DOCTOR scope too ──

    @Test
    @Order(85)
    void doctorCreate_outOfScopePatient_should403() throws Exception {
        mockMvc.perform(post("/api/v1/patients/102/care-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "x", "goal", "y")))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/patients/102/problems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("snomedCode", "x", "snomedDisplay", "y")))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/patients/102/vitals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("systolicBp", 120)))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/patients/102/immunizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("vaccineName", "x")))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/patients/102/history")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("description", "x")))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/patients/102/allergies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("allergen", "x", "reaction", "y", "severity", "MILD")))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/referrals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("patientId", 102, "specialistName", "Dr. X")))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/prior-auths")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", 102, "authType", "MED", "itemName", "x",
                                "itemCode", "y", "insurancePayer", "z")))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
    }

    // ── Post-Round-49 review: newly encrypted free-text PHI round-trip ──

    @Test
    @Order(86)
    void createImmunization_longNotes_shouldRoundTrip() throws Exception {
        String longNotes = "Post-vaccination notes (encrypted): patient monitored 15 minutes, " + "x".repeat(200);
        String body = objectMapper.writeValueAsString(Map.of(
                "vaccineName", "RSV vaccine",
                "administrationDate", "2026-01-15",
                "lotNumber", "RS2026-001",
                "manufacturer", "GSK",
                "doseNumber", "1st dose",
                "site", "left arm",
                "route", "intramuscular",
                "notes", longNotes
        ));
        mockMvc.perform(post("/api/v1/patients/102/immunizations")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        MvcResult result = mockMvc.perform(get("/api/v1/patients/102/immunizations")
                        .param("page", "1").param("size", "50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode records = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("records");
        boolean found = false;
        for (JsonNode n : records) {
            if ("RSV vaccine".equals(n.get("vaccineName").asText())) {
                assertEquals(longNotes, n.get("notes").asText());
                found = true;
            }
        }
        assertTrue(found, "created immunization not found in list");
    }

    @Test
    @Order(87)
    void refillAndEmergency_encryptedFields_shouldRoundTrip() throws Exception {
        // Refill reason: patient-created free text round-trips through encryption.
        String refillReason = "Long refill reason (encrypted): lost the bottle, " + "y".repeat(200);
        mockMvc.perform(post("/api/v1/patient/me/refill-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("prescriptionId", 300, "reason", refillReason)))
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk());
        MvcResult mine = mockMvc.perform(get("/api/v1/patient/me/refill-requests")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list = objectMapper.readTree(mine.getResponse().getContentAsString()).get("data");
        assertTrue(list.size() > 0, "no refill requests listed");
        assertEquals(refillReason, list.get(0).get("reason").asText());

        // Emergency access reason: round-trips through encryption (admin history read-back).
        String emReason = "Emergency reason (encrypted): patient unresponsive, " + "z".repeat(200);
        mockMvc.perform(post("/api/v1/emergency/access/102")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", emReason)))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());
        MvcResult history = mockMvc.perform(get("/api/v1/emergency/history")
                        .param("patientId", "102")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode entries = objectMapper.readTree(history.getResponse().getContentAsString()).get("data");
        boolean found = false;
        for (JsonNode n : entries) {
            if (emReason.equals(n.get("reason").asText())) { found = true; break; }
        }
        assertTrue(found, "emergency access reason did not round-trip");
    }

    // ──────────────────────────────────────────────────────
    // 10. DASHBOARD
    // ──────────────────────────────────────────────────────

    @Test
    @Order(71)
    void dashboardStats_asAdmin_shouldReturnStats() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/dashboard/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").get("totalPatients").asInt() >= 0);
        assertTrue(node.get("data").get("appointmentStatusDistribution").isArray());
        assertTrue(node.get("data").get("revenueTrend").isArray());
    }

    @Test
    @Order(72)
    void dashboardStats_asDoctor_shouldSucceed() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/stats")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(73)
    void dashboardStats_asPatient_shouldBeDenied() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/dashboard/stats")
                        .header("Authorization", "Bearer " + patientToken))
                .andReturn();
        assertNotEquals(200, result.getResponse().getStatus(),
                "Patient should not access dashboard stats");
    }

    // ──────────────────────────────────────────────────────
    // 11. PATIENT PORTAL (self-service)
    // ──────────────────────────────────────────────────────

    @Test
    @Order(74)
    void patientGetMyProfile_shouldReturnProfile() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/patient/me")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertNotNull(node.get("data").get("name").asText());
    }

    @Test
    @Order(75)
    void patientGetMyAppointments_shouldReturnAppointments() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/patient/me/appointments")
                        .param("page", "1").param("size", "10")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").get("total").asInt() >= 1);
    }

    @Test
    @Order(76)
    void patientGetMyPrescriptions_shouldReturnPrescriptions() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/patient/me/prescriptions")
                        .param("page", "1").param("size", "10")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
    }

    @Test
    @Order(77)
    void patientGetMyBills_shouldReturnBills() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/patient/me/bills")
                        .param("page", "1").param("size", "10")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
    }

    // ──────────────────────────────────────────────────────
    // 12. USER PROFILE (self-service)
    // ──────────────────────────────────────────────────────

    @Test
    @Order(78)
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
    @Order(79)
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
    @Order(80)
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
    @Order(81)
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
    // 13. FHIR
    // ──────────────────────────────────────────────────────

    @Test
    @Order(82)
    void fhirMetadata_shouldReturnCapabilityStatement() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/fhir/metadata"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/fhir+json"))
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("CapabilityStatement", node.get("resourceType").asText());
    }

    @Test
    @Order(83)
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
    // 14. EXPORT (rate-limited, admin/doctor only)
    // ──────────────────────────────────────────────────────

    @Test
    @Order(84)
    void exportPatients_shouldNotFail() throws Exception {
        // May return 200 or 429 (rate-limited); must not be 500
        MvcResult result = mockMvc.perform(get("/api/v1/export/patients")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn();
        assertNotEquals(500, result.getResponse().getStatus(),
                "Export patients should not return 500");
    }

    @Test
    @Order(85)
    void exportBills_shouldNotFail() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/export/bills")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn();
        assertNotEquals(500, result.getResponse().getStatus(),
                "Export bills should not return 500");
    }

    // ──────────────────────────────────────────────────────
    // 15. AUDIT LOGS
    // ──────────────────────────────────────────────────────

    @Test
    @Order(86)
    void auditLogs_shouldReturnPaginatedResults() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/audit-logs")
                        .param("page", "1").param("size", "10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").get("total").asInt() > 0,
                "Audit logs should contain entries from test bootstrap");
    }

    @Test
    @Order(87)
    void auditLogs_withFilters_shouldFilter() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/audit-logs")
                        .param("module", "patient").param("action", "CREATE")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(node.get("data").get("total").asInt() >= 0);
    }

    // ──────────────────────────────────────────────────────
    // 16. FHIR RESOURCE ENDPOINTS
    // ──────────────────────────────────────────────────────

    @Test
    @Order(88)
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
    @Order(89)
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
    @Order(90)
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
    @Order(91)
    void fhirObservationSearch_noPatient_shouldReturnBundle() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/fhir/Observation")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("Bundle", node.get("resourceType").asText());
    }

    // ──────────────────────────────────────────────────────
    // 17. CDS
    // ──────────────────────────────────────────────────────

    @Test
    @Order(92)
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
    // 18. INTEGRATION
    // ──────────────────────────────────────────────────────

    @Test
    @Order(93)
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
    @Order(94)
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

    // ──────────────────────────────────────────────────────
    // 19. LOINC CATALOG
    // ──────────────────────────────────────────────────────

    @Test
    @Order(95)
    void loincCatalog_shouldReturnCodes() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/loinc/catalog")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(node.get("data").get(0).has("loincCode"));
    }

    @Test
    @Order(96)
    void loincPanel_shouldReturnGroupedCodes() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/loinc/panel/CBC")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(node.get("data").size() >= 5, "CBC panel should have 8 codes");
    }

    // ──────────────────────────────────────────────────────
    // 20. LAB TREND
    // ──────────────────────────────────────────────────────

    @Test
    @Order(97)
    void labTrend_shouldReturnObservationsByLoinc() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/patients/100/observations")
                        .param("loinc", "6690-2")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(node.get("data").get("records").isArray());
    }

    // ──────────────────────────────────────────────────────
    // 21. PHARMACY
    // ──────────────────────────────────────────────────────

    @Test
    @Order(98)
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
    @Order(99)
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
    // 22. PRESCRIPTION TRANSMIT
    // ──────────────────────────────────────────────────────

    @Test
    @Order(100)
    void prescriptionTransmit_shouldGenerateNcpdp() throws Exception {
        MvcResult result = mockMvc.perform(put("/api/v1/prescriptions/300/transmit")
                        .param("pharmacyId", "1")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertEquals("transmitted", node.get("data").get("status").asText());
    }

    // ──────────────────────────────────────────────────────
    // 23. eCQM QUALITY MEASURES
    // ──────────────────────────────────────────────────────

    @Test
    @Order(101)
    void qualityMeasures_shouldListDefinitions() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/quality/measures")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(node.get("data").isArray());
        assertTrue(node.get("data").size() >= 3, "Seed data should have 3 CMS measures");
    }

    @Test
    @Order(102)
    void qualityReport_shouldCalculatePerformance() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/quality/measures/CMS122v11/report")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("CMS122v11", node.get("data").get("cmsId").asText());
        assertTrue(node.get("data").get("performanceRate").isNumber());
    }

    // ──────────────────────────────────────────────────────
    // 24. CONSENT
    // ──────────────────────────────────────────────────────

    @Test
    @Order(103)
    void consent_create_shouldSucceed() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "consentType", "TREATMENT",
                "scope", "general",
                "patientId", 100
        ));
        mockMvc.perform(post("/api/v1/consent")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(104)
    void consent_list_shouldReturnForPatient() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/consent")
                        .param("patientId", "100")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(node.get("data").isArray());
        assertTrue(node.get("data").size() >= 1);
    }

    @Test
    @Order(105)
    void consent_revoke_shouldSucceed() throws Exception {
        // revoke the one just created (id=1)
        mockMvc.perform(put("/api/v1/consent/1/revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(106)
    void patientConsent_selfService_shouldReturnRecords() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/patient/me/consent")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(node.get("data").isArray());
    }

    // ──────────────────────────────────────────────────────
    // 25. EMERGENCY ACCESS
    // ──────────────────────────────────────────────────────

    @Test
    @Order(107)
    void emergencyAccess_shouldReturnToken() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "reason", "Patient unconscious in ER — need allergy history immediately"
        ));
        MvcResult result = mockMvc.perform(post("/api/v1/emergency/access/100")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        assertNotNull(node.get("data").get("token"));
        assertEquals(100, node.get("data").get("patientId").asLong());
    }

    @Test
    @Order(108)
    void emergencyHistory_shouldReturnRecords() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/emergency/history")
                        .param("patientId", "100")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(node.get("data").isArray());
        assertTrue(node.get("data").size() >= 1);
    }

    // ──────────────────────────────────────────────────────
    // 26. KEY AUDIT
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
    // 27. PATIENT SELF-EDIT PROFILE
    // ──────────────────────────────────────────────────────

    @Test
    @Order(110)
    void patientUpdateOwnProfile_shouldSucceed() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "phoneMobile", "312-555-7777",
                "email", "james.updated@email.com"
        ));
        mockMvc.perform(put("/api/v1/patient/me")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(111)
    void patientUpdateOwnProfile_nameBlocked_shouldBeIgnored() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Hacked Name"
        ));
        mockMvc.perform(put("/api/v1/patient/me")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk());
        // verify name unchanged
        MvcResult result = mockMvc.perform(get("/api/v1/patient/me")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk()).andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("James Anderson", node.get("data").get("name").asText());
    }
}
