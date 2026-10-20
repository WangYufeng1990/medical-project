package com.example.medical;

import com.example.medical.common.config.DevSchemaGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round 54.2 review: an existing local database must be able to take the new table
 * by starting the application — with its own data, and more than once.
 * <p>
 * This class prepares a database as the previous release left it — every table in
 * {@code sql/schema.sql} except {@code chat_conversation}, {@code schema_version}
 * recording the old version, and <b>history of its own</b> written before the new
 * build ever ran — and then boots the real application against it. The messages are
 * planted rather than seeded so that "the upgrade preserved what was already there"
 * is a claim about rows this build never created.
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

    /** History planted before the upgrade: a pair the seed step does not create itself. */
    private static final long LEGACY_MESSAGE_OLD_ID = 900L;
    private static final long LEGACY_MESSAGE_NEW_ID = 901L;
    private static final long LEGACY_PATIENT_ID = 103L;
    private static final long LEGACY_STAFF_ID = 1L;

    @DynamicPropertySource
    static void prepareThePreviousReleaseDatabase(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", () -> UPGRADE_URL);
        DriverManagerDataSource probe = new DriverManagerDataSource(UPGRADE_URL, "sa", "");
        try (Connection connection = probe.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new EncodedResource(new ByteArrayResource(schemaWithoutConversations()), StandardCharsets.UTF_8));
        }
        JdbcTemplate jdbc = new JdbcTemplate(probe);
        jdbc.update("INSERT INTO schema_version (version) VALUES (?)", PREVIOUS_SCHEMA_VERSION);
        plantLegacyMessage(jdbc, LEGACY_MESSAGE_OLD_ID, "legacy message before the upgrade",
                LocalDateTime.now().minusDays(2).withNano(0));
        plantLegacyMessage(jdbc, LEGACY_MESSAGE_NEW_ID, "legacy message, newer one",
                LocalDateTime.now().minusDays(2).plusMinutes(10).withNano(0));
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void startingOnAnOlderDatabase_shouldUpgradeItAndKeepTheDataItAlreadyHad() {
        // The application reached this test at all: the guard did not refuse to start.
        assertEquals(DevSchemaGuard.SCHEMA_VERSION, recordedVersion(),
                "the upgraded database must record the version this build expects");
        assertEquals(1, count("SELECT COUNT(*) FROM schema_version WHERE version = ?", PREVIOUS_SCHEMA_VERSION),
                "the previous version stays in the history");

        // The planted history is untouched, row by row.
        Map<String, Object> oldRow = jdbcTemplate.queryForMap(
                "SELECT id, content, sender_id, sender_type, receiver_id, receiver_type "
                        + "FROM message WHERE id = ?", LEGACY_MESSAGE_OLD_ID);
        assertEquals("legacy message before the upgrade", oldRow.get("content"));
        assertEquals(LEGACY_STAFF_ID, ((Number) oldRow.get("sender_id")).longValue());
        assertEquals("STAFF", oldRow.get("sender_type"));
        assertEquals(LEGACY_PATIENT_ID, ((Number) oldRow.get("receiver_id")).longValue());
        assertEquals("PATIENT", oldRow.get("receiver_type"));
        assertEquals(1, count("SELECT COUNT(*) FROM message WHERE id = ?", LEGACY_MESSAGE_NEW_ID),
                "the newer planted message is still there too");
        assertEquals(10, count("SELECT COUNT(*) FROM message"),
                "two planted messages plus the eight the seed step adds");

        // Three threads: the two seeded pairs and the planted one.
        assertEquals(3, count("SELECT COUNT(*) FROM chat_conversation"));
        Map<String, Object> plantedThread = jdbcTemplate.queryForMap(
                "SELECT message_count, revision, last_message_id FROM chat_conversation "
                        + "WHERE party_a_type = 'PATIENT' AND party_a_id = ? "
                        + "AND party_b_type = 'STAFF' AND party_b_id = ?",
                LEGACY_PATIENT_ID, LEGACY_STAFF_ID);
        assertEquals(2, ((Number) plantedThread.get("message_count")).intValue(),
                "the planted history became one thread of two messages");
        assertEquals(2L, ((Number) plantedThread.get("revision")).longValue());
        assertEquals(LEGACY_MESSAGE_NEW_ID, ((Number) plantedThread.get("last_message_id")).longValue(),
                "the thread points at the newer planted message");

        Map<String, Object> seededThread = jdbcTemplate.queryForMap(
                "SELECT message_count, revision FROM chat_conversation "
                        + "WHERE party_a_type = 'PATIENT' AND party_a_id = 100");
        assertEquals(6, ((Number) seededThread.get("message_count")).intValue(),
                "patient 100 has six seeded messages");
        assertEquals(6L, ((Number) seededThread.get("revision")).longValue());
    }

    @Test
    void restartingTheApplication_shouldChangeNothing() {
        Map<String, Object> before = snapshot();

        // A real restart: a second application context against the same database,
        // running the same SQL initialization, guard and backfill a new JVM would.
        // Command-line arguments, not default properties: the h2 profile file would
        // otherwise win and the restart would create the developer's file database.
        try (ConfigurableApplicationContext restarted = new SpringApplicationBuilder(MedicalApplication.class)
                .profiles("h2")
                .run("--spring.datasource.url=" + UPGRADE_URL,
                        "--spring.h2.console.enabled=false",
                        "--app.rate-limit.enabled=false",
                        "--server.port=0")) {
            assertTrue(restarted.isActive(), "the restarted application must come up");
        }

        assertEquals(before, snapshot(),
                "a restart must not duplicate, recount or lose messages, threads or version rows");
        assertDoesNotThrow(() -> new DevSchemaGuard(jdbcTemplate, UPGRADE_URL).run());
        assertEquals(before, snapshot(), "and the guard alone must stay a no-op");
    }

    // ── helpers ─────────────────────────────────────────────────────

    /** What must survive a restart unchanged. */
    private Map<String, Object> snapshot() {
        return Map.of(
                "version", recordedVersion(),
                "versionRows", count("SELECT COUNT(*) FROM schema_version"),
                "messages", count("SELECT COUNT(*) FROM message"),
                "legacyMessages", List.of(
                        count("SELECT COUNT(*) FROM message WHERE id = ? AND content = ?",
                                LEGACY_MESSAGE_OLD_ID, "legacy message before the upgrade"),
                        count("SELECT COUNT(*) FROM message WHERE id = ? AND content = ?",
                                LEGACY_MESSAGE_NEW_ID, "legacy message, newer one")),
                "conversations", count("SELECT COUNT(*) FROM chat_conversation"),
                "countedMessages", count("SELECT COALESCE(SUM(message_count), 0) FROM chat_conversation"),
                "revisions", count("SELECT COALESCE(SUM(revision), 0) FROM chat_conversation"));
    }

    private Integer recordedVersion() {
        return jdbcTemplate.queryForObject("SELECT MAX(version) FROM schema_version", Integer.class);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private static void plantLegacyMessage(JdbcTemplate jdbc, long id, String content, LocalDateTime sentAt) {
        jdbc.update("INSERT INTO message (id, sender_id, sender_type, receiver_id, receiver_type, content, "
                        + "is_read, create_time, update_time, is_deleted, version) VALUES (?,?,?,?,?,?,1,?,?,0,0)",
                id, LEGACY_STAFF_ID, "STAFF", LEGACY_PATIENT_ID, "PATIENT", content,
                java.sql.Timestamp.valueOf(sentAt), java.sql.Timestamp.valueOf(sentAt));
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
