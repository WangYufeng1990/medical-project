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
 * <p>
 * A database one version behind is <b>upgraded</b>, not rejected, when the step
 * between the two versions is additive and already applied — which is what
 * {@code sql/schema.sql} does on the next start (it creates what is missing and
 * never alters an existing table). The guard verifies the object the step adds and
 * only then records the new version, so "upgraded" is never a blind stamp; an
 * upgrade it cannot complete still fails loudly, pointing at the reset. Round 54.2
 * review: refusing v3 databases forced a data-losing delete for a purely additive
 * change.
 */
@Slf4j
@Component
@Profile({"dev", "h2"})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DevSchemaGuard implements CommandLineRunner {

    /** Baseline for the current sql/schema.sql. Bump on every schema change. */
    public static final int SCHEMA_VERSION = 4;

    /**
     * The steps this build can finish on an older database: version → the table that
     * version adds. Only additive changes qualify, because re-running
     * {@code sql/schema.sql} creates missing tables but never alters existing ones.
     */
    private static final java.util.Map<Integer, String> ADDITIVE_STEPS =
            java.util.Map.of(4, "chat_conversation");

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
            record(SCHEMA_VERSION);
            log.info("Local schema version recorded: v{}", SCHEMA_VERSION);
            return;
        }
        if (recorded == SCHEMA_VERSION) {
            log.debug("Local schema version v{} matches the build", recorded);
            return;
        }
        if (recorded > SCHEMA_VERSION) {
            throw new IllegalStateException(String.format(
                    "Local database schema is v%d, newer than this build's v%d (%s). "
                    + "Start the build that owns this database, or point this one at another H2_DB_PATH.",
                    recorded, SCHEMA_VERSION, datasourceUrl));
        }
        upgrade(recorded);
    }

    /**
     * Applies the additive steps between {@code recorded} and {@link #SCHEMA_VERSION}.
     * Each step is recorded only after the object it adds is really present, so an
     * unapplied step is still a loud failure rather than a version stamp that lies.
     */
    private void upgrade(int recorded) {
        for (int version = recorded + 1; version <= SCHEMA_VERSION; version++) {
            String table = ADDITIVE_STEPS.get(version);
            if (table == null || !tableExists(table)) {
                throw new IllegalStateException(String.format(
                        "Local database schema is v%d and this build needs v%d (%s). "
                        + "The step to v%d adds table '%s', which this database does not have: "
                        + "spring.sql.init never alters an existing database, so the upgrade cannot be "
                        + "applied. On the h2/dev profile restart once so sql/schema.sql runs; if the table "
                        + "is still missing (or on MySQL, which does not run that file), apply the DDL from "
                        + "sql/schema.sql by hand. The last resort is deleting the local database file "
                        + "(default: ~/.medical-dev/data/, or the path in H2_DB_PATH) and restarting.",
                        recorded, SCHEMA_VERSION, datasourceUrl, version, table));
            }
            record(version);
            log.info("Local schema upgraded: v{} → v{} (added {})", version - 1, version, table);
        }
    }

    private void record(int version) {
        jdbcTemplate.update("INSERT INTO schema_version (version) VALUES (?)", version);
    }

    private boolean tableExists(String table) {
        Integer found = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_NAME) = LOWER(?)",
                Integer.class, table);
        return found != null && found > 0;
    }
}
