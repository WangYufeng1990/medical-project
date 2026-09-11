package com.example.medical.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Fails fast when the local database was created by an older version of
 * {@code sql/schema.sql}.
 * <p>
 * Every statement in that file is {@code CREATE TABLE IF NOT EXISTS} and
 * {@code spring.sql.init.mode=always}, so a schema edit is silently ignored by
 * an existing database file — the missing column then only surfaces when
 * something first reads or writes it (this is how {@code audit_log.prev_hash}
 * went missing and broke chain-hash writes). This guard records the schema
 * version the file was built with and refuses to start on a mismatch, pointing
 * at the one-line fix.
 * <p>
 * <strong>Bump {@link #SCHEMA_VERSION} in the same commit as any change to
 * {@code sql/schema.sql}.</strong>
 */
@Slf4j
@Component
@Profile({"dev", "h2"})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DevSchemaGuard implements CommandLineRunner {

    /** Baseline for the current sql/schema.sql. Bump on every schema change. */
    static final int SCHEMA_VERSION = 1;

    private final JdbcTemplate jdbcTemplate;
    private final String datasourceUrl;

    public DevSchemaGuard(JdbcTemplate jdbcTemplate,
                          @Value("${spring.datasource.url:unknown}") String datasourceUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.datasourceUrl = datasourceUrl;
    }

    @Override
    public void run(String... args) {
        // getObject (never getInt) — getInt maps SQL NULL to 0, which would make
        // a fresh database look like "version 0" and fail the check below.
        Integer recorded = jdbcTemplate.query(
                "SELECT MAX(version) FROM schema_version",
                rs -> {
                    if (!rs.next()) return null;
                    Number max = (Number) rs.getObject(1);
                    return max == null ? null : max.intValue();
                });

        if (recorded == null) {
            jdbcTemplate.update("INSERT INTO schema_version (version) VALUES (?)", SCHEMA_VERSION);
            log.info("Local schema version recorded: v{}", SCHEMA_VERSION);
            return;
        }
        if (recorded != SCHEMA_VERSION) {
            throw new IllegalStateException(String.format(
                    "Local database schema is v%d but this build expects v%d (%s). "
                    + "spring.sql.init never alters existing tables, so the new columns are missing. "
                    + "Delete the local database file (default: ~/.medical-dev/data/, or the path in "
                    + "H2_DB_PATH) and restart to rebuild schema + seed data.",
                    recorded, SCHEMA_VERSION, datasourceUrl));
        }
        log.debug("Local schema version v{} matches the build", recorded);
    }
}
