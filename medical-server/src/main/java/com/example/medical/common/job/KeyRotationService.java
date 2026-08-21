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
            for (TableColumn tc : ENCRYPTED_COLUMNS) {
                migrateColumn(tc);
            }
            complete = true;
            AesCryptoUtil.markRotationComplete();
            log.info("Key rotation complete — all {} encrypted columns migrated", ENCRYPTED_COLUMNS.size());
            writeAudit("ROTATION_COMPLETE", "All legacy ciphertexts re-encrypted with current key");
        } catch (Exception e) {
            log.error("Key rotation aborted — will retry on next safety check", e);
        } finally {
            running = false;
        }
    }

    private void migrateColumn(TableColumn tc) {
        String table = tc.table;
        String column = tc.column;
        int totalMigrated = 0;
        int offset = 0;

        // Full scan with OFFSET paging (Review III C2): the old predicate
        // `NOT LIKE '01%'` excluded every v1 row, so nothing was ever
        // re-encrypted after a rotation. Now every non-null row is examined
        // and only rows still encrypted with the PREVIOUS key are rewritten.
        while (true) {
            final int off = offset;
            Integer batchCount = transactionTemplate.execute(status -> {
                String sql = "SELECT id, " + column + " FROM " + table
                           + " WHERE " + column + " IS NOT NULL"
                           + " ORDER BY id LIMIT " + BATCH_SIZE + " OFFSET " + off;
                List<RowData> rows = jdbc.query(sql,
                        (rs, i) -> new RowData(rs.getLong("id"), rs.getString(column)));

                if (rows.isEmpty()) return 0;

                int count = 0;
                for (RowData row : rows) {
                    if (!AesCryptoUtil.isEncryptedWithPreviousKey(row.ciphertext)) continue;
                    String newCipher = AesCryptoUtil.reencrypt(row.ciphertext);
                    if (newCipher != null) {
                        jdbc.update("UPDATE " + table + " SET " + column + " = ? WHERE id = ?",
                                newCipher, row.id);
                        count++;
                    } else {
                        log.warn("Rotation: skipping row id={} in {}.{} — decryption failed", row.id, table, column);
                    }
                }
                return count;
            });

            if (batchCount == null || batchCount == 0) break;

            offset += BATCH_SIZE;
            totalMigrated += batchCount;
            remainingByTable.put(table + "." + column, totalMigrated);
            log.info("Rotation: migrated {} rows in {}.{} (total so far: {})", batchCount, table, column, totalMigrated);

            try {
                Thread.sleep(SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        remainingByTable.put(table + "." + column, 0);
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

    record TableColumn(String table, String column) {}
    record RowData(long id, String ciphertext) {}
}
