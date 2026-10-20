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
    void failsFastWhenTheAdditiveStepIsMissing() {
        // The step to v4 adds chat_conversation. Without it the upgrade cannot be
        // completed (e.g. MySQL, which never runs sql/schema.sql), so the guard must
        // still refuse rather than stamp a version the database does not match.
        jdbcTemplate.execute("DROP TABLE chat_conversation");
        jdbcTemplate.update("INSERT INTO schema_version (version) VALUES (?)",
                DevSchemaGuard.SCHEMA_VERSION - 1);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new DevSchemaGuard(jdbcTemplate, "jdbc:h2:file:/tmp/stale-db").run());

        assertTrue(e.getMessage().contains("v" + (DevSchemaGuard.SCHEMA_VERSION - 1)),
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
                DevSchemaGuard.SCHEMA_VERSION - 1);

        assertThrows(IllegalStateException.class,
                () -> new DevSchemaGuard(jdbcTemplate, "jdbc:h2:file:/tmp/other-schema-db").run());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schema_version", Integer.class),
                "a table found in another schema must not let the upgrade record a version");
    }

    @Test
    void failsFastOnAnOlderSchemaThisBuildCannotComplete() {
        // No additive step is known from v2 to v3, so the guard cannot upgrade it.
        jdbcTemplate.update("INSERT INTO schema_version (version) VALUES (?)",
                DevSchemaGuard.SCHEMA_VERSION - 2);

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
