package com.example.medical.common.job;

import com.example.medical.common.audit.KeyAudit;
import com.example.medical.common.audit.KeyAuditRepository;
import com.example.medical.common.config.AesCryptoUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Slf4j
@Component
@RequiredArgsConstructor
public class KeyRotationService {

    private static final int BATCH_SIZE = 500;
    private static final long SLEEP_MS = 500;

    /**
     * Every column encrypted via AesAttributeConverter (incl. the Round 48/49
     * free-text fields and the Batch 4 prescription fields) — all must be
     * migrated on key rotation or their ciphertext becomes unreadable
     * (Review III C2).
     */
    private static final List<TableColumn> ENCRYPTED_COLUMNS = List.of(
            new TableColumn("patient", "ssn"),
            new TableColumn("patient", "name"),
            new TableColumn("patient", "date_of_birth"),
            new TableColumn("patient", "primary_care_provider"),
            new TableColumn("patient", "phone_mobile"),
            new TableColumn("patient", "phone_home"),
            new TableColumn("patient", "phone_work"),
            new TableColumn("patient", "email"),
            new TableColumn("patient", "address_line1"),
            new TableColumn("patient", "address_line2"),
            new TableColumn("patient", "city"),
            new TableColumn("patient", "state"),
            new TableColumn("patient", "zip_code"),
            new TableColumn("patient", "emergency_contact_name"),
            new TableColumn("patient", "emergency_contact_phone"),
            new TableColumn("patient", "insurance_payer"),
            new TableColumn("patient", "insurance_member_id"),
            new TableColumn("patient", "insurance_group_number"),
            new TableColumn("patient", "medical_history"),
            new TableColumn("patient", "allergies"),
            new TableColumn("sys_user", "phone"),
            new TableColumn("sys_user", "email"),
            new TableColumn("sys_user", "state_license_number"),
            new TableColumn("sys_user", "dea_number"),
            new TableColumn("message", "content"),
            new TableColumn("agent_run_result", "draft"),
            new TableColumn("agent_review_decision", "final_text"),
            new TableColumn("agent_review_decision", "reason"),
            new TableColumn("bill", "insurance_claim_number"),
            new TableColumn("prescription", "diagnosis"),
            new TableColumn("prescription", "icd10_codes"),
            new TableColumn("prescription", "dea_number"),
            new TableColumn("prescription", "pharmacy_name"),
            new TableColumn("prescription", "pharmacy_phone"),
            new TableColumn("prescription_item", "drug_name"),
            new TableColumn("prescription_item", "dosage"),
            new TableColumn("prescription_item", "sig"),
            new TableColumn("prescription_item", "notes"),
            new TableColumn("appointment", "chief_complaint"),
            new TableColumn("appointment", "description"),
            new TableColumn("appointment", "notes"),
            new TableColumn("referral", "diagnosis"),
            new TableColumn("referral", "reason"),
            new TableColumn("referral", "notes"),
            new TableColumn("charge", "notes"),
            new TableColumn("prior_auth", "notes"),
            new TableColumn("allergy_entry", "allergen"),
            new TableColumn("allergy_entry", "reaction"),
            new TableColumn("care_plan", "goal"),
            new TableColumn("care_plan", "interventions"),
            new TableColumn("care_plan", "notes"),
            new TableColumn("immunization", "notes"),
            new TableColumn("medical_history_entry", "description"),
            new TableColumn("problem", "notes"),
            new TableColumn("vital_sign", "notes"),
            new TableColumn("cds_override", "override_reason"),
            new TableColumn("refill_request", "reason"),
            new TableColumn("refill_request", "review_notes"),
            new TableColumn("emergency_access", "reason")
    );

    private final JdbcTemplate jdbc;
    private final KeyAuditRepository keyAuditRepo;
    private final TransactionTemplate transactionTemplate;
    private final Executor rotationExecutor;

    private final Map<String, Integer> remainingByTable = new ConcurrentHashMap<>();
    /** Pause between batches, to keep a rotation from saturating the database; tests set it to 0. */
    private long pauseMillis = SLEEP_MS;
    private volatile boolean running;
    private volatile boolean complete;

    // ── public status ──

    public boolean isRunning() { return running; }
    public boolean isComplete() { return complete; }
    public Map<String, Integer> getRemainingByTable() { return new LinkedHashMap<>(remainingByTable); }

    // ── lifecycle ──

    @PostConstruct
    void startIfNeeded() {
        if (AesCryptoUtil.isRotationActive()) {
            log.info("Key rotation detected — starting background re-encryption ({}) columns", ENCRYPTED_COLUMNS.size());
            rotationExecutor.execute(this::runBatchRotation);
        }
    }

    /**
     * Called from the admin API to trigger a runtime key rotation.
     * The caller must have already called {@link AesCryptoUtil#rotate(String, String)}.
     */
    /** Test entry point: a scan under test should not spend its time pacing itself. */
    void setPauseMillisForTest(long millis) {
        this.pauseMillis = millis;
    }

    /** Test entry point: resets the published state without spawning the background run. */
    void resetStateForTest() {
        complete = false;
        remainingByTable.clear();
    }

    public void startRuntimeRotation() {
        complete = false;
        remainingByTable.clear();
        log.info("Runtime key rotation triggered — starting background re-encryption");
        rotationExecutor.execute(this::runBatchRotation);
    }

    @Scheduled(cron = "0 0 3 * * ?")
    void safetyCheck() {
        if (running || !AesCryptoUtil.isRotationActive() || complete) return;
        log.info("Rotation safety check — resuming migration if incomplete");
        rotationExecutor.execute(this::runBatchRotation);
    }

    // ── core ──

    void runBatchRotation() {
        if (running) {
            log.info("Rotation already in progress, skipping concurrent run");
            return;
        }
        running = true;
        try {
            int remaining = 0;
            for (TableColumn tc : ENCRYPTED_COLUMNS) {
                remaining += migrateColumn(tc);
            }
            if (remaining > 0) {
                // Retiring the old key now would make exactly these rows unreadable.
                log.warn("Key rotation scanned every column but {} rows are still on the previous key — "
                        + "completion withheld, the old key stays in service", remaining);
                writeAudit("ROTATION_INCOMPLETE",
                        remaining + " rows re-encrypted were not reachable; old key retained");
                return;
            }
            complete = true;
            AesCryptoUtil.markRotationComplete();
            log.info("Key rotation complete — all {} encrypted columns verified migrated", ENCRYPTED_COLUMNS.size());
            writeAudit("ROTATION_COMPLETE", "All legacy ciphertexts re-encrypted with current key");
        } catch (Exception e) {
            log.error("Key rotation aborted — will retry on next safety check", e);
        } finally {
            running = false;
        }
    }

    private int migrateColumn(TableColumn tc) {
        return migrateColumn(tc, BATCH_SIZE);
    }

    /**
     * Walks one column by id and re-encrypts every row still on the previous key.
     * <p>
     * Keyset paging rather than OFFSET, and the walk ends when a batch comes back
     * <em>empty</em> — never because a batch happened to need no rewrite, which is how a
     * resumed rotation used to stop at its first already-migrated page and leave the
     * rest of the column behind (R53-03). What is reported is a second, read-only pass
     * that counts the rows still on the previous key, so a scan cannot claim zero
     * remaining while leaving ciphertext only the retired key can read.
     *
     * @return rows of this column still on the previous key when the pass ends
     */
    int migrateColumn(TableColumn tc, int batchSize) {
        String table = tc.table;
        String column = tc.column;
        int migrated = 0;
        long lastId = -1;

        while (true) {
            final long after = lastId;
            BatchOutcome outcome = transactionTemplate.execute(status -> {
                List<RowData> rows = jdbc.query(
                        "SELECT id, " + column + " FROM " + table
                                + " WHERE " + column + " IS NOT NULL AND id > ? ORDER BY id LIMIT " + batchSize,
                        (rs, i) -> new RowData(rs.getLong("id"), rs.getString(column)), after);
                int rewrote = 0;
                for (RowData row : rows) {
                    if (!AesCryptoUtil.isEncryptedWithPreviousKey(row.ciphertext)) continue;
                    String newCipher = AesCryptoUtil.reencrypt(row.ciphertext);
                    if (newCipher == null) {
                        log.warn("Rotation: skipping row id={} in {}.{} — decryption failed", row.id, table, column);
                        continue;
                    }
                    if (migrateRow(table, column, row.id, row.ciphertext, newCipher) == 0) {
                        // The ciphertext read a moment ago is gone: someone edited the row.
                        // Rewriting now would overwrite their content, so leave it to the
                        // verification pass to report and the next run to migrate.
                        log.warn("Rotation: row id={} in {}.{} changed while rotating — left for the next pass",
                                row.id, table, column);
                        continue;
                    }
                    rewrote++;
                }
                return new BatchOutcome(rows.size(), rewrote, rows.isEmpty() ? -1 : rows.get(rows.size() - 1).id());
            });

            if (outcome == null || outcome.scanned() == 0) break;
            migrated += outcome.migrated();
            lastId = outcome.lastId();

            if (pauseMillis <= 0) continue;
            try {
                Thread.sleep(pauseMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        int remaining = countPreviousKeyRows(tc);
        remainingByTable.put(table + "." + column, remaining);
        log.info("Rotation: {}.{} — migrated {} rows, {} still on the previous key",
                table, column, migrated, remaining);
        return remaining;
    }

    /**
     * Rewrites one row. Package-visible so its compare-and-set can be exercised directly.
     * The read ciphertext is part of the predicate: an edit that landed between the
     * SELECT and the UPDATE wins, and this returns 0 instead of overwriting it (R53-03).
     */
    int migrateRow(String table, String column, long id, String readCiphertext, String newCiphertext) {
        return jdbc.update("UPDATE " + table + " SET " + column + " = ? WHERE id = ? AND " + column + " = ?",
                newCiphertext, id, readCiphertext);
    }

    /** Rows of this column still encrypted with the previous key — the truth a scan must not round to zero. */
    int countPreviousKeyRows(TableColumn tc) {
        Integer count = jdbc.query(
                "SELECT " + tc.column + " FROM " + tc.table + " WHERE " + tc.column + " IS NOT NULL",
                rs -> {
                    int n = 0;
                    while (rs.next()) {
                        if (AesCryptoUtil.isEncryptedWithPreviousKey(rs.getString(1))) n++;
                    }
                    return n;
                });
        return count == null ? 0 : count;
    }

    private void writeAudit(String eventType, String detail) {
        try {
            KeyAudit audit = new KeyAudit();
            audit.setEventType(eventType);
            audit.setKeyVersion("v1");
            audit.setDetail(detail);
            keyAuditRepo.save(audit);
        } catch (Exception e) {
            log.error("Failed to write key rotation audit", e);
        }
    }

    // ── types ──

    /**
     * The rotation list, as {@code table.column}, for the guard test that failed to exist before
     * Round 57.2: a column encrypted by a converter but missing here would keep working until the
     * old key is removed, and then silently stop being readable.
     */
    public static java.util.Set<String> encryptedColumnNames() {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (TableColumn column : ENCRYPTED_COLUMNS) {
            names.add(column.table() + "." + column.column());
        }
        return names;
    }

    record TableColumn(String table, String column) {}
    record RowData(long id, String ciphertext) {}

    /** What one batch did: how many rows it looked at, how many it rewrote, and where to resume. */
    record BatchOutcome(int scanned, int migrated, long lastId) {}
}
