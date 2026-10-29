package com.example.medical.module.chat.service;

import com.example.medical.common.config.AesCryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Encrypts the agent text that predates its encryption (Round 57.2 review).
 * <p>
 * The three columns were plaintext before the converters were added, so reading them through the
 * converters would return {@code [DECRYPT_FAILED]} until they are migrated. This runs once at
 * startup, is idempotent (a value that is already ciphertext is skipped, so a second start does not
 * encrypt twice), and writes through raw SQL because the JPA mapping would apply the converter on
 * the way in as well.
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class AgentTextEncryptionBackfill implements ApplicationRunner {

    private static final int BATCH = 500;

    private static final List<String[]> COLUMNS = List.of(
            new String[]{"agent_run_result", "draft"},
            new String[]{"agent_review_decision", "final_text"},
            new String[]{"agent_review_decision", "reason"});

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        for (String[] column : COLUMNS) {
            encryptColumn(column[0], column[1]);
        }
    }

    /** Visible for the test that proves the upgrade of a v10 database. */
    public int encryptColumn(String table, String column) {
        List<java.util.Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, " + column + " AS stored FROM " + table + " WHERE " + column
                        + " IS NOT NULL AND " + column + " <> ''");
        int migrated = 0;
        for (java.util.Map<String, Object> row : rows) {
            Object value = row.get("stored");
            if (!(value instanceof String text) || AesCryptoUtil.isCiphertext(text)) continue;
            jdbc.update("UPDATE " + table + " SET " + column + " = ? WHERE id = ?",
                    AesCryptoUtil.encrypt(text), row.get("id"));
            migrated++;
        }
        if (migrated > 0) {
            log.info("Encrypted {} plaintext value(s) in {}.{}", migrated, table, column);
        }
        return migrated;
    }
}
