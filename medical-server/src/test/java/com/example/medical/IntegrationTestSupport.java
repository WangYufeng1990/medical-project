package com.example.medical;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared harness for the integration tests: one Spring context, one in-memory H2
 * database, and {@code cleanup-test-data.sql} applied before each test class so
 * no class inherits another's writes.
 * <p>
 * Subclasses hold only their own tests plus, when needed, their own tokens:
 * <pre>
 * &#64;TestInstance(TestInstance.Lifecycle.PER_CLASS)
 * class XIntegrationTest extends IntegrationTestSupport {
 *     private String adminToken;
 *
 *     &#64;BeforeAll
 *     void authenticate() throws Exception { adminToken = login("admin", "admin123"); }
 * }
 * </pre>
 * Log in per class, never in a shared static field — a suite-wide token makes
 * every test depend on whichever test happened to run first.
 */
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
abstract class IntegrationTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String login(String username, String password) throws Exception {
        return token("/api/v1/auth/login", username, password);
    }

    protected String patientLogin(String username, String password) throws Exception {
        return token("/api/v1/patient/login", username, password);
    }

    private String token(String path, String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", username, "password", password));
        MvcResult result = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, node.get("code").asInt());
        return node.get("data").get("token").asText();
    }
}
