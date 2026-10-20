package com.example.medical;

import com.example.medical.common.config.DevSchemaGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round 54.2 review: an existing local database must be able to take the new table
 * by starting the application, not by deleting itself.
 * <p>
 * This class boots the real application against a database prepared as the previous
 * release left it — every table in {@code sql/schema.sql} except
 * {@code chat_conversation}, with {@code schema_version} recording the old version —
 * and then asserts that the normal startup path (SQL initialization, the schema
 * guard, the seed step, the application-ready backfill) carries it forward with its
 * data intact. Booting in another test class would share the probe database, so the
 * preparation and the boot live together here.
 */
@SpringBootTest
@ActiveProfiles("h2")
@TestPropertySource(properties = {
        "spring.h2.console.enabled=false",
        "app.rate-limit.enabled=false",
})
class SchemaUpgradeIntegrationTest {

    private static final String UPGRADE_URL =
            "jdbc:h2:mem:medical_upgrade_probe;MODE=MySQL;DB_CLOSE_DELAY=-1";
    private static final int PREVIOUS_SCHEMA_VERSION = 3;

    @DynamicPropertySource
    static void prepareThePreviousReleaseDatabase(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", () -> UPGRADE_URL);
        DriverManagerDataSource probe = new DriverManagerDataSource(UPGRADE_URL, "sa", "");
        try (Connection connection = probe.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new EncodedResource(new ByteArrayResource(schemaWithoutConversations()), StandardCharsets.UTF_8));
        }
        new JdbcTemplate(probe).update("INSERT INTO schema_version (version) VALUES (?)", PREVIOUS_SCHEMA_VERSION);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void startingOnAnOlderDatabase_shouldUpgradeItAndKeepItsData() {
        // The application reached this test at all: the guard did not refuse to start.
        assertEquals(DevSchemaGuard.SCHEMA_VERSION, recordedVersion(),
                "the upgraded database must record the version this build expects");
        assertEquals(2, count("SELECT COUNT(*) FROM chat_conversation"),
                "the seeded history has two pairs: one thread each, patients 100 and 101 with doctor1");
        assertEquals(1, count("SELECT COUNT(*) FROM schema_version WHERE version = ?", PREVIOUS_SCHEMA_VERSION),
                "the previous version stays in the history");

        // Seeded history is untouched and now belongs to a thread.
        assertEquals(8, count("SELECT COUNT(*) FROM message"), "seeded messages must survive the upgrade");
        Map<String, Object> thread = jdbcTemplate.queryForMap(
                "SELECT party_a_type, party_a_id, party_b_type, party_b_id, message_count, revision "
                        + "FROM chat_conversation WHERE party_a_type = 'PATIENT' AND party_a_id = 100");
        assertEquals("STAFF", thread.get("party_b_type"), "the canonical pair puts the patient first");
        assertEquals(2L, ((Number) thread.get("party_b_id")).longValue());
        assertEquals(6, ((Number) thread.get("message_count")).intValue(),
                "patient 100 has six seeded messages");
        assertEquals(6L, ((Number) thread.get("revision")).longValue());

        Map<String, Object> other = jdbcTemplate.queryForMap(
                "SELECT message_count, revision FROM chat_conversation "
                        + "WHERE party_a_type = 'PATIENT' AND party_a_id = 101");
        assertEquals(2, ((Number) other.get("message_count")).intValue(),
                "patient 101 has two seeded messages");
        assertEquals(2L, ((Number) other.get("revision")).longValue());
    }

    @Test
    void restartingAnUpgradedDatabase_shouldBeANoOp() {
        int versionsBefore = count("SELECT COUNT(*) FROM schema_version");
        int threadsBefore = count("SELECT COUNT(*) FROM chat_conversation");

        // The guard is what a restart runs first; it must accept its own upgrade.
        assertDoesNotThrow(() -> new DevSchemaGuard(jdbcTemplate, UPGRADE_URL).run());

        assertEquals(versionsBefore, count("SELECT COUNT(*) FROM schema_version"),
                "a matching version must not append another row");
        assertEquals(threadsBefore, count("SELECT COUNT(*) FROM chat_conversation"));
    }

    // ── helpers ─────────────────────────────────────────────────────

    private Integer recordedVersion() {
        return jdbcTemplate.queryForObject("SELECT MAX(version) FROM schema_version", Integer.class);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    /** {@code sql/schema.sql} as the previous release had it: everything but the new table. */
    private static byte[] schemaWithoutConversations() throws Exception {
        ClassPathResource resource = new ClassPathResource("sql/schema.sql");
        String schema = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int blockStart = schema.indexOf("-- Round 54.2:");
        int statementStart = schema.indexOf("CREATE TABLE IF NOT EXISTS chat_conversation (");
        int blockEnd = schema.indexOf(");", statementStart) + 2;
        assertTrue(blockStart >= 0 && statementStart > blockStart && blockEnd > statementStart,
                "sql/schema.sql must still contain the chat_conversation block this test strips");
        return (schema.substring(0, blockStart) + schema.substring(blockEnd)).getBytes(StandardCharsets.UTF_8);
    }
}
