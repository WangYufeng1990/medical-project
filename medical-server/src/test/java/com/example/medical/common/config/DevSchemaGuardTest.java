package com.example.medical.common.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The local schema guard must turn silent schema drift into a loud startup
 * failure — and must not fire on a healthy or freshly created database.
 * Runs against the real {@code sql/schema.sql} so it also proves that file
 * defines {@code schema_version}.
 */
class DevSchemaGuardTest {

    /** The version just before the v4 table step (chat_conversation). */
    private static final int BEFORE_THE_CONVERSATION_STEP = 3;
    /** The version just before the v5 column step (message.client_message_id). */
    private static final int BEFORE_THE_COLUMN_STEP = 4;
    /** A version this build has no contiguous path from: the steps start at 4. */
    private static final int BEFORE_ANY_KNOWN_STEP = 2;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:dev-schema-guard-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "");
        ScriptUtils.executeSqlScript(dataSource.getConnection(),
                new ClassPathResource("sql/schema.sql"));
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    private Integer recordedVersion() {
        return jdbcTemplate.queryForObject("SELECT MAX(version) FROM schema_version", Integer.class);
    }

    @Test
    void recordsBaselineOnFreshDatabase() {
        new DevSchemaGuard(jdbcTemplate, "jdbc:h2:mem:fresh").run();

        assertEquals(DevSchemaGuard.SCHEMA_VERSION, recordedVersion());
    }

    @Test
    void isIdempotentWhenVersionMatches() {
        new DevSchemaGuard(jdbcTemplate, "jdbc:h2:mem:same").run();
        new DevSchemaGuard(jdbcTemplate, "jdbc:h2:mem:same").run();

        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schema_version", Integer.class);
        assertEquals(1, rows, "a matching version must not append another row");
    }

    @Test
    void upgradesAnAdditiveStepAndRecordsIt() {
        // Round 54.2: one version behind with the new table already created by
        // sql/schema.sql is an upgrade, not a reason to delete the database.
        jdbcTemplate.update("INSERT INTO schema_version (version) VALUES (?)",
                DevSchemaGuard.SCHEMA_VERSION - 1);

        new DevSchemaGuard(jdbcTemplate, "jdbc:h2:mem:upgradable").run();

        assertEquals(DevSchemaGuard.SCHEMA_VERSION, recordedVersion(),
                "the upgrade records the version this build expects");
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schema_version", Integer.class),
                "the previous version stays in the history");
    }

    @Test
    void appliesTheColumnStepItself() {
        // Round 54.3: the step adds a column to an existing table, which
        // CREATE TABLE IF NOT EXISTS cannot do, so the guard runs the step's DDL.
        // A minimal v4 database: the tables the step needs, without the new column.
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:column-upgrade-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate probe = new JdbcTemplate(dataSource);
        probe.execute("CREATE TABLE schema_version (id BIGINT NOT NULL AUTO_INCREMENT, version INT NOT NULL, "
                + "applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (id))");
        probe.execute("CREATE TABLE chat_conversation (id BIGINT NOT NULL PRIMARY KEY)");
        probe.execute("CREATE TABLE agent_run (id BIGINT NOT NULL PRIMARY KEY)");
        probe.execute("CREATE TABLE agent_capability (id BIGINT NOT NULL PRIMARY KEY)");
        probe.execute("CREATE TABLE agent_run_result (id BIGINT NOT NULL PRIMARY KEY)");
        probe.execute("CREATE TABLE agent_review_claim (id BIGINT NOT NULL PRIMARY KEY)");
        probe.execute("CREATE TABLE agent_review_decision (id BIGINT NOT NULL PRIMARY KEY, reason VARCHAR(500))");
        probe.execute("CREATE TABLE message (id BIGINT NOT NULL AUTO_INCREMENT, sender_id BIGINT NOT NULL, "
                + "sender_type VARCHAR(10) NOT NULL, receiver_id BIGINT NOT NULL, receiver_type VARCHAR(10) NOT NULL, "
                + "content TEXT NOT NULL, is_read TINYINT DEFAULT 0, create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, is_deleted TINYINT NOT NULL DEFAULT 0, "
                + "version INT NOT NULL DEFAULT 0, PRIMARY KEY (id))");
        probe.update("INSERT INTO schema_version (version) VALUES (?)", BEFORE_THE_COLUMN_STEP);

        new DevSchemaGuard(probe, "jdbc:h2:mem:column-upgrade").run();

        assertEquals(DevSchemaGuard.SCHEMA_VERSION, probe.queryForObject(
                "SELECT MAX(version) FROM schema_version", Integer.class));
        assertEquals(1, probe.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_NAME = 'MESSAGE' AND COLUMN_NAME = 'CLIENT_MESSAGE_ID'", Integer.class),
                "the guard must add the column it records a version for");
        probe.update("INSERT INTO message (sender_id, sender_type, receiver_id, receiver_type, content, "
                + "client_message_id) VALUES (1,'STAFF',100,'PATIENT','first','probe-key')");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> probe.update("INSERT INTO message (sender_id, sender_type, receiver_id, receiver_type, "
                        + "content, client_message_id) VALUES (1,'STAFF',100,'PATIENT','second','probe-key')"),
                "the added index must enforce the sender-scoped key");
    }

    @Test
    void retypesAnExistingColumnSoCiphertextFits() {
        // A v10 database whose reason column is VARCHAR(500): the encrypted form of a 500-character
        // reason is longer than that, so v11 has to change the type, not just record itself.
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:retype-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate probe = new JdbcTemplate(dataSource);
        probe.execute("CREATE TABLE schema_version (id BIGINT NOT NULL AUTO_INCREMENT, "
                + "version INT NOT NULL, applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "PRIMARY KEY (id))");
        probe.execute("CREATE TABLE chat_conversation (id BIGINT NOT NULL PRIMARY KEY)");
        probe.execute("CREATE TABLE agent_run (id BIGINT NOT NULL PRIMARY KEY)");
        probe.execute("CREATE TABLE agent_capability (id BIGINT NOT NULL PRIMARY KEY)");
        probe.execute("CREATE TABLE agent_run_result (id BIGINT NOT NULL PRIMARY KEY)");
        probe.execute("CREATE TABLE agent_review_claim (id BIGINT NOT NULL PRIMARY KEY)");
        probe.execute("CREATE TABLE agent_review_decision (id BIGINT NOT NULL PRIMARY KEY, "
                + "reason VARCHAR(500))");
        probe.execute("CREATE TABLE message (id BIGINT NOT NULL AUTO_INCREMENT, "
                + "sender_id BIGINT NOT NULL, sender_type VARCHAR(10) NOT NULL, "
                + "receiver_id BIGINT NOT NULL, receiver_type VARCHAR(10) NOT NULL, "
                + "PRIMARY KEY (id))");
        probe.update("INSERT INTO schema_version (version) VALUES (10)");

        new DevSchemaGuard(probe, "jdbc:h2:mem:retype-upgrade").run();

        // The reviewer's measurement: 500 ASCII characters encrypt to 1058 characters.
        String wide = "a".repeat(1058);
        probe.update("INSERT INTO agent_review_decision (id, reason) VALUES (1, ?)", wide);
        assertEquals(wide, probe.queryForObject(
                "SELECT reason FROM agent_review_decision WHERE id = 1", String.class));
        assertEquals(DevSchemaGuard.SCHEMA_VERSION, probe.queryForObject(
                "SELECT MAX(version) FROM schema_version", Integer.class));
    }

    @Test
    void repairsADatabaseThatRecordedV11WithANarrowColumn() {
        // The previous build could record v11 while the column was still VARCHAR(500), and its
        // early return kept the capacity check from running. Such a database must be repaired.
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:repair-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate probe = new JdbcTemplate(dataSource);
        probe.execute("CREATE TABLE schema_version (id BIGINT NOT NULL AUTO_INCREMENT, "
                + "version INT NOT NULL, applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "PRIMARY KEY (id))");
        probe.execute("CREATE TABLE chat_conversation (id BIGINT NOT NULL PRIMARY KEY)");
        probe.execute("CREATE TABLE agent_run (id BIGINT NOT NULL PRIMARY KEY)");
        probe.execute("CREATE TABLE agent_capability (id BIGINT NOT NULL PRIMARY KEY)");
        probe.execute("CREATE TABLE agent_run_result (id BIGINT NOT NULL PRIMARY KEY)");
        probe.execute("CREATE TABLE agent_review_claim (id BIGINT NOT NULL PRIMARY KEY)");
        probe.execute("CREATE TABLE agent_review_decision (id BIGINT NOT NULL PRIMARY KEY, "
                + "reason VARCHAR(500))");
        probe.execute("CREATE TABLE message (id BIGINT NOT NULL AUTO_INCREMENT, "
                + "sender_id BIGINT NOT NULL, sender_type VARCHAR(10) NOT NULL, "
                + "receiver_id BIGINT NOT NULL, receiver_type VARCHAR(10) NOT NULL, "
                + "PRIMARY KEY (id))");
        probe.update("INSERT INTO schema_version (version) VALUES (11)");

        new DevSchemaGuard(probe, "jdbc:h2:mem:repair-upgrade").run();

        // The 500-character reason becomes 1058 characters of ciphertext, which only fits now.
        String wide = "b".repeat(1058);
        probe.update("INSERT INTO agent_review_decision (id, reason) VALUES (2, ?)", wide);
        assertEquals(wide, probe.queryForObject(
                "SELECT reason FROM agent_review_decision WHERE id = 2", String.class));
        assertEquals(DevSchemaGuard.SCHEMA_VERSION, probe.queryForObject(
                "SELECT MAX(version) FROM schema_version", Integer.class));

        // A second start is a no-op.
        new DevSchemaGuard(probe, "jdbc:h2:mem:repair-upgrade").run();
        assertEquals(wide, probe.queryForObject(
                "SELECT reason FROM agent_review_decision WHERE id = 2", String.class));
    }

    @Test
    void finishesAPartiallyAppliedStep() {
        // An upgrade interrupted after the column was added leaves the version at 4
        // with no index. The next start must create the index, not re-add the column.
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:partial-upgrade-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate probe = new JdbcTemplate(dataSource);
        probe.execute("CREATE TABLE schema_version (id BIGINT NOT NULL AUTO_INCREMENT, version INT NOT NULL, "
                + "applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (id))");
        probe.execute("CREATE TABLE chat_conversation (id BIGINT NOT NULL PRIMARY KEY)");
        probe.execute("CREATE TABLE agent_run (id BIGINT NOT NULL PRIMARY KEY)");
        probe.execute("CREATE TABLE agent_capability (id BIGINT NOT NULL PRIMARY KEY)");
        probe.execute("CREATE TABLE agent_run_result (id BIGINT NOT NULL PRIMARY KEY)");
        probe.execute("CREATE TABLE agent_review_claim (id BIGINT NOT NULL PRIMARY KEY)");
        probe.execute("CREATE TABLE agent_review_decision (id BIGINT NOT NULL PRIMARY KEY, reason VARCHAR(500))");
        probe.execute("CREATE TABLE message (id BIGINT NOT NULL AUTO_INCREMENT, sender_id BIGINT NOT NULL, "
                + "sender_type VARCHAR(10) NOT NULL, receiver_id BIGINT NOT NULL, receiver_type VARCHAR(10) NOT NULL, "
                + "content TEXT NOT NULL, is_read TINYINT DEFAULT 0, client_message_id VARCHAR(100) DEFAULT NULL, "
                + "create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, is_deleted TINYINT NOT NULL DEFAULT 0, "
                + "version INT NOT NULL DEFAULT 0, PRIMARY KEY (id))");
        probe.update("INSERT INTO message (sender_id, sender_type, receiver_id, receiver_type, content) "
                + "VALUES (1,'STAFF',100,'PATIENT','a message from before the upgrade')");
        probe.update("INSERT INTO schema_version (version) VALUES (?)", BEFORE_THE_COLUMN_STEP);

        new DevSchemaGuard(probe, "jdbc:h2:mem:partial-upgrade").run();

        assertEquals(DevSchemaGuard.SCHEMA_VERSION, probe.queryForObject(
                "SELECT MAX(version) FROM schema_version", Integer.class));
        assertEquals(1, probe.queryForObject("SELECT COUNT(*) FROM message WHERE content = ?", Integer.class,
                "a message from before the upgrade"), "the interrupted upgrade must keep the messages");
        probe.update("INSERT INTO message (sender_id, sender_type, receiver_id, receiver_type, content, "
                + "client_message_id) VALUES (1,'STAFF',100,'PATIENT','after','partial-key')");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> probe.update("INSERT INTO message (sender_id, sender_type, receiver_id, receiver_type, "
                        + "content, client_message_id) VALUES (1,'STAFF',100,'PATIENT','again','partial-key')"),
                "the index must exist after the finished upgrade");
    }

    @Test
    void failsFastWhenTheAdditiveStepIsMissing() {
        // The step to v4 adds chat_conversation. Without it the upgrade cannot be
        // completed (e.g. MySQL, which never runs sql/schema.sql), so the guard must
        // still refuse rather than stamp a version the database does not match.
        jdbcTemplate.execute("DROP TABLE chat_conversation");
        jdbcTemplate.update("INSERT INTO schema_version (version) VALUES (?)",
                BEFORE_THE_CONVERSATION_STEP);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new DevSchemaGuard(jdbcTemplate, "jdbc:h2:file:/tmp/stale-db").run());

        assertTrue(e.getMessage().contains("v" + BEFORE_THE_CONVERSATION_STEP),
                "message must name the version found: " + e.getMessage());
        assertTrue(e.getMessage().contains("H2_DB_PATH"),
                "message must point at the fix: " + e.getMessage());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schema_version", Integer.class),
                "a rejected database must not be stamped with the new version");
    }

    @Test
    void failsFastWhenOnlyAnotherSchemaHasTheTable() {
        // The upgrade step must look at the schema this connection writes to. A
        // same-named table elsewhere in the database (on MySQL: another catalog) is
        // not the table the application is about to use.
        jdbcTemplate.execute("DROP TABLE chat_conversation");
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS elsewhere");
        jdbcTemplate.execute("CREATE TABLE elsewhere.chat_conversation (id BIGINT PRIMARY KEY)");
        jdbcTemplate.update("INSERT INTO schema_version (version) VALUES (?)",
                BEFORE_THE_CONVERSATION_STEP);

        assertThrows(IllegalStateException.class,
                () -> new DevSchemaGuard(jdbcTemplate, "jdbc:h2:file:/tmp/other-schema-db").run());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schema_version", Integer.class),
                "a table found in another schema must not let the upgrade record a version");
    }

    @Test
    void failsFastOnAnOlderSchemaThisBuildCannotComplete() {
        // No additive step is known from v2 to v3, so the guard cannot upgrade it.
        jdbcTemplate.update("INSERT INTO schema_version (version) VALUES (?)",
                BEFORE_ANY_KNOWN_STEP);

        assertThrows(IllegalStateException.class,
                () -> new DevSchemaGuard(jdbcTemplate, "jdbc:h2:file:/tmp/ancient-db").run());
    }

    @Test
    void failsFastOnNewerSchema() {
        jdbcTemplate.update("INSERT INTO schema_version (version) VALUES (?)",
                DevSchemaGuard.SCHEMA_VERSION + 1);

        assertThrows(IllegalStateException.class,
                () -> new DevSchemaGuard(jdbcTemplate, "jdbc:h2:file:/tmp/newer-db").run());
    }
}
