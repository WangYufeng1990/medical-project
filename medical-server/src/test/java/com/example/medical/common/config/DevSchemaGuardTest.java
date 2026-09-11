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
    void failsFastOnOlderSchema() {
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
    void failsFastOnNewerSchema() {
        jdbcTemplate.update("INSERT INTO schema_version (version) VALUES (?)",
                DevSchemaGuard.SCHEMA_VERSION + 1);

        assertThrows(IllegalStateException.class,
                () -> new DevSchemaGuard(jdbcTemplate, "jdbc:h2:file:/tmp/newer-db").run());
    }
}
