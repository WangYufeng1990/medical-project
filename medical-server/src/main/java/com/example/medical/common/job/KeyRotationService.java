package com.example.medical.common.job;

import com.example.medical.common.audit.KeyAudit;
import com.example.medical.common.audit.KeyAuditRepository;
import com.example.medical.common.config.AesCryptoUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
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

    private static final List<TableColumn> ENCRYPTED_COLUMNS = List.of(
            new TableColumn("patient", "ssn"),
            new TableColumn("patient", "name"),
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
            new TableColumn("patient", "date_of_birth"),
            new TableColumn("sys_user", "phone"),
            new TableColumn("sys_user", "email"),
            new TableColumn("sys_user", "state_license_number"),
            new TableColumn("sys_user", "dea_number"),
            new TableColumn("message", "content"),
            new TableColumn("bill", "insurance_claim_number"),
            new TableColumn("prescription", "dea_number")
    );

    private final DataSource dataSource;
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

    @Scheduled(cron = "0 0 3 * * ?")
    void safetyCheck() {
        if (!AesCryptoUtil.isRotationActive() || complete) return;
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

        while (true) {
            Integer batchCount = transactionTemplate.execute(status -> {
                List<RowData> rows = fetchLegacyRows(table, column);
                if (rows.isEmpty()) return 0;

                int count = 0;
                for (RowData row : rows) {
                    String newCipher = AesCryptoUtil.reencrypt(row.ciphertext);
                    if (newCipher != null) {
                        updateRow(table, column, row.id, newCipher);
                        count++;
                    } else {
                        log.warn("Rotation: skipping row id={} in {}.{} — decryption failed", row.id, table, column);
                    }
                }
                return count;
            });

            if (batchCount == null || batchCount == 0) break;

            totalMigrated += batchCount;
            remainingByTable.put(table + "." + column, countLegacyRows(table, column));
            log.info("Rotation: migrated {} rows in {}.{} (total so far: {})", batchCount, table, column, totalMigrated);

            try {
                Thread.sleep(SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private List<RowData> fetchLegacyRows(String table, String column) {
        List<RowData> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, " + column + " FROM " + table
                     + " WHERE " + column + " IS NOT NULL"
                     + " AND " + column + " NOT LIKE '01%'"
                     + " LIMIT " + BATCH_SIZE)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new RowData(rs.getLong("id"), rs.getString(column)));
                }
            }
        } catch (Exception e) {
            log.error("Rotation: failed to fetch legacy rows from {}.{}", table, column, e);
        }
        return rows;
    }

    private void updateRow(String table, String column, long id, String newCipher) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE " + table + " SET " + column + " = ? WHERE id = ?")) {
            ps.setString(1, newCipher);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("Rotation: failed to update {}.{} id={}", table, column, id, e);
        }
    }

    private int countLegacyRows(String table, String column) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM " + table
                     + " WHERE " + column + " IS NOT NULL"
                     + " AND " + column + " NOT LIKE '01%'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) {
            // ignore
        }
        return 0;
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
