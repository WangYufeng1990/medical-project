package com.example.medical.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

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
 * A database a version behind is <b>upgraded</b>, not rejected. A step that only
 * adds a table needs nothing from the guard: {@code sql/schema.sql} creates it on
 * the next start. A step that has to change an existing table cannot rely on that
 * file at all — {@code CREATE TABLE IF NOT EXISTS} never alters — so the step carries
 * its own DDL and the guard runs it. Every step is verified after it is applied, and
 * a version is recorded only once its objects really exist, so "upgraded" is never a
 * blind stamp. A step the guard cannot complete still fails loudly, pointing at the
 * reset. Round 54.2 review: refusing v3 databases forced a data-losing delete for a
 * purely additive change; Round 54.3 needs the column case.
 */
@Slf4j
@Component
@Profile({"dev", "h2"})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DevSchemaGuard implements CommandLineRunner {

    /** Baseline for the current sql/schema.sql. Bump on every schema change. */
    public static final int SCHEMA_VERSION = 10;

    /**
     * The upgrades this build can apply to an older database, in order. A step names
     * the objects it needs and, when the object is one {@code sql/schema.sql} cannot
     * add, the DDL that adds it.
     */
    private static final java.util.List<UpgradeStep> UPGRADE_STEPS = java.util.List.of(
            // v4: sql/schema.sql creates the table on the next start.
            new UpgradeStep(4, "chat_conversation", null, null, null, null),
            // v5: a column on an existing table — CREATE TABLE IF NOT EXISTS cannot do
            // this, so the step carries the DDL. The unique index is scoped to the
            // sender's typed identity, and nulls are distinct in both MySQL and H2, so
            // keyless messages never collide. Column and index are checked and added
            // separately: an interrupted upgrade may have completed one of them, and
            // re-running the whole group would then fail on the part that exists.
            new UpgradeStep(5, "message", "client_message_id",
                    "ALTER TABLE message ADD COLUMN client_message_id VARCHAR(100) DEFAULT NULL",
                    "uk_message_sender_client_id",
                    "CREATE UNIQUE INDEX uk_message_sender_client_id "
                            + "ON message (sender_type, sender_id, client_message_id)"),
            // v6: another table, created by sql/schema.sql on the next start.
            new UpgradeStep(6, "agent_run", null, null, null, null),
            // v7: capability digests, likewise created by the schema script.
            new UpgradeStep(7, "agent_capability", null, null, null, null),
            // v8/v9: the review result and its claim, both tables the script creates.
            new UpgradeStep(8, "agent_run_result", null, null, null, null),
            new UpgradeStep(9, "agent_review_claim", null, null, null, null),
            new UpgradeStep(10, "agent_review_decision", null, null, null, null));

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
     * Applies the steps between {@code recorded} and {@link #SCHEMA_VERSION}. A step is
     * applied only when its objects are missing, verified again afterwards, and
     * recorded only then — an upgrade that silently did nothing stays a loud failure.
     */
    private void upgrade(int recorded) {
        java.util.List<UpgradeStep> steps = UPGRADE_STEPS.stream()
                .filter(step -> step.version() > recorded)
                .toList();
        // The steps must form a chain from the recorded version upwards: jumping over a
        // version would record a schema this build cannot vouch for.
        if (steps.isEmpty() || steps.get(0).version() != recorded + 1) {
            throw new IllegalStateException(unknownChainMessage(recorded));
        }
        for (UpgradeStep step : steps) {
            applyMissingParts(step);
            if (!stepApplied(step)) {
                throw new IllegalStateException(failureMessage(recorded, step));
            }
            record(step.version());
            log.info("Local schema upgraded: v{} → v{} ({})", step.version() - 1, step.version(), step.describe());
        }
    }

    private String unknownChainMessage(int recorded) {
        return String.format(
                "Local database schema is v%d and this build needs v%d (%s), but it has no upgrade step "
                + "from v%d: the versions in between are older than anything this build can verify. "
                + "Delete the local database file (default: ~/.medical-dev/data/, or the path in "
                + "H2_DB_PATH) and restart to rebuild schema + seed data.",
                recorded, SCHEMA_VERSION, datasourceUrl, recorded);
    }

    private String failureMessage(int recorded, UpgradeStep step) {
        return String.format(
                "Local database schema is v%d and this build needs v%d (%s). "
                + "The step to v%d needs %s, which this database does not have and the build could not "
                + "add: spring.sql.init never alters an existing database. On the h2/dev profile restart "
                + "once so sql/schema.sql runs; if it is still missing (or on MySQL, which does not run "
                + "that file), apply the DDL from sql/schema.sql by hand. The last resort is deleting the "
                + "local database file (default: ~/.medical-dev/data/, or the path in H2_DB_PATH) and "
                + "restarting.",
                recorded, SCHEMA_VERSION, datasourceUrl, step.version(), step.describe());
    }

    /**
     * Adds only the parts of a step that are missing. A step that was interrupted
     * between its column and its index must be finishable on the next start, so the
     * two are never applied as one all-or-nothing group.
     */
    private void applyMissingParts(UpgradeStep step) {
        if (step.column() != null && step.columnDdl() != null && !columnExists(step.table(), step.column())) {
            jdbcTemplate.execute(step.columnDdl());
        }
        if (step.index() != null && step.indexDdl() != null && !indexExists(step.table(), step.index())) {
            jdbcTemplate.execute(step.indexDdl());
        }
    }

    private boolean stepApplied(UpgradeStep step) {
        return tableExists(step.table())
                && (step.column() == null || columnExists(step.table(), step.column()))
                && (step.index() == null || indexExists(step.table(), step.index()));
    }

    private void record(int version) {
        jdbcTemplate.update("INSERT INTO schema_version (version) VALUES (?)", version);
    }

    /**
     * Whether the table exists in the schema this connection writes to.
     * <p>
     * Asked per connection rather than through {@code INFORMATION_SCHEMA}: that view
     * lists every schema the login can see, so on MySQL a same-named table in another
     * catalog would satisfy the check while the application's own schema still lacked
     * it — and the upgrade would record a version the database does not match. JDBC's
     * metadata is scoped to the connection's catalog/schema on both MySQL and H2.
     */
    private boolean tableExists(String table) {
        DataSource dataSource = jdbcTemplate.getDataSource();
        if (dataSource == null) return false;
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet tables = metaData.getTables(
                    connection.getCatalog(), connection.getSchema(), null, null)) {
                while (tables.next()) {
                    if (table.equalsIgnoreCase(tables.getString("TABLE_NAME"))) return true;
                }
            }
            return false;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not inspect the local schema to verify the upgrade step", e);
        }
    }

    private boolean columnExists(String table, String column) {
        DataSource dataSource = jdbcTemplate.getDataSource();
        if (dataSource == null) return false;
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet columns = metaData.getColumns(
                    connection.getCatalog(), connection.getSchema(), null, null)) {
                while (columns.next()) {
                    if (table.equalsIgnoreCase(columns.getString("TABLE_NAME"))
                            && column.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                        return true;
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not inspect the local schema to verify the upgrade step", e);
        }
    }

    /**
     * Whether the unique index behind a constraint exists. Matched by prefix because a
     * database may name the index after the constraint: H2 stores
     * {@code uk_message_sender_client_id} as {@code UK_MESSAGE_SENDER_CLIENT_ID_INDEX_…},
     * while MySQL keeps the declared name.
     */
    private boolean indexExists(String table, String index) {
        DataSource dataSource = jdbcTemplate.getDataSource();
        if (dataSource == null) return false;
        String prefix = index.toUpperCase(Locale.ROOT);
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            // H2 matches the table pattern case-sensitively against the stored upper-case
            // name; MySQL's driver does not care. Ask for both spellings.
            for (String tablePattern : java.util.List.of(table.toUpperCase(Locale.ROOT), table)) {
                try (ResultSet indexes = metaData.getIndexInfo(
                        connection.getCatalog(), connection.getSchema(), tablePattern, false, false)) {
                    while (indexes.next()) {
                        String name = indexes.getString("INDEX_NAME");
                        if (name != null && name.toUpperCase(Locale.ROOT).startsWith(prefix)) return true;
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not inspect the local schema to verify the upgrade step", e);
        }
    }

    /** One upgrade: the version it produces, what it needs, and the DDL for each part. */
    private record UpgradeStep(int version, String table, String column, String columnDdl,
                               String index, String indexDdl) {

        String describe() {
            StringBuilder what = new StringBuilder("table '" + table + "'");
            if (column != null) what.append(", column '").append(column).append("'");
            if (index != null) what.append(", index '").append(index).append("'");
            return what.toString();
        }
    }
}
