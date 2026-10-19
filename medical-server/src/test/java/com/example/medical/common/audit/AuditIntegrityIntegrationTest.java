package com.example.medical.common.audit;

import com.example.medical.IntegrationTestSupport;
import com.example.medical.common.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R53-08: the audit hash chain must survive (a) concurrent appends and
 * (b) the precision the {@code create_time} column actually stores.
 * <p>
 * Both were broken in the dev database: rows 3 and 4 chained from row 2
 * because two {@code audit-*} threads read the same head, and the hash covered
 * {@code LocalDateTime.toString()} while the column kept only microseconds.
 */
class AuditIntegrityIntegrationTest extends IntegrationTestSupport {

    private static final int WRITERS = 16;

    @Autowired
    private AuditLogWriter auditLogWriter;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuditLogService auditLogService;

    /**
     * Each test asserts on a chain of its own making: the shared in-memory
     * database keeps rows from earlier classes, and one earlier broken row would
     * mask the break a later test injects. Production never deletes audit rows —
     * this only clears the fixture.
     */
    @BeforeEach
    void clearAuditLog() {
        jdbcTemplate.update("DELETE FROM audit_log");
    }

    @Test
    void concurrentAsyncWritesChainOntoDistinctPredecessors() throws InterruptedException {
        long baseline = maxId();
        for (int i = 0; i < WRITERS; i++) {
            auditLogWriter.writeAsync(1L, "admin", null, "integration", "CONCURRENT_" + i,
                    null, "concurrent-" + i, "127.0.0.1", Instant.now());
        }

        List<Map<String, Object>> rows = pollRows(baseline, WRITERS);
        assertEquals(WRITERS, rows.size(), "every concurrent append must persist a row");

        Set<Object> predecessors = new HashSet<>();
        for (Map<String, Object> row : rows) {
            predecessors.add(row.get("prev_hash"));
        }
        assertEquals(WRITERS, predecessors.size(),
                "two rows chained from the same predecessor — the chain forked");

        long previous = baseline;
        for (Map<String, Object> row : rows) {
            long id = ((Number) row.get("id")).longValue();
            assertTrue(id > previous, "appends must land in the order they chained");
            previous = id;
        }
        String expectedPredecessor = headHash(baseline);
        for (Map<String, Object> row : rows) {
            assertEquals(expectedPredecessor, row.get("prev_hash"),
                    "row " + row.get("id") + " did not chain onto its predecessor");
            expectedPredecessor = (String) row.get("row_hash");
        }
    }

    @Test
    void microsecondPrecisionSurvivesTheRoundTrip() throws Exception {
        long baseline = maxId();
        Instant nanosecondEventTime = Instant.parse("2026-01-01T00:00:00.123456789Z");

        auditLogWriter.writeAsync(1L, "admin", null, "integration", "PRECISION",
                "precision", "detail", "127.0.0.1", nanosecondEventTime);

        List<Map<String, Object>> rows = pollRows(baseline, 1);
        assertEquals(1, rows.size(), "the row must be written");
        long id = ((Number) rows.get(0).get("id")).longValue();

        AuditLog stored = auditLogRepository.findById(id).orElseThrow();
        assertNotNull(stored.getRowHash());
        assertEquals(0, stored.getCreateTime().getNano() % 1_000,
                "create_time keeps microseconds, so the hash must not cover more");
        assertEquals(stored.getRowHash(), stored.computeRowHash(),
                "a row written at nanosecond precision must still verify after a round trip");
    }

    @Test
    void aRewrittenRowIsReportedAsARowHashBreak() throws Exception {
        long id = writeOne("rewrite");

        jdbcTemplate.update("UPDATE audit_log SET detail = 'rewritten' WHERE id = ?", id);

        AuditLogService.ChainBreak broken = auditLogService.verifyIntegrity();
        assertNotNull(broken, "a rewritten row must break the log");
        assertEquals(id, broken.rowId());
        assertEquals(AuditLogService.Reason.ROW_HASH, broken.reason(),
                "changed content must be reported as a digest mismatch, not a chain break");
    }

    @Test
    void aMisLinkedRowIsReportedAsAChainBreak() throws Exception {
        writeOne("anchor");
        long id = writeOne("mislink");

        // A forked write looks exactly like this: the row is internally
        // consistent, it just points at a predecessor that is not its neighbour.
        AuditLog row = auditLogRepository.findById(id).orElseThrow();
        row.setPrevHash("deadbeef");
        jdbcTemplate.update("UPDATE audit_log SET prev_hash = ?, row_hash = ? WHERE id = ?",
                "deadbeef", row.computeRowHash(), id);

        AuditLogService.ChainBreak broken = auditLogService.verifyIntegrity();
        assertNotNull(broken, "a row pointing at the wrong predecessor must break the log");
        assertEquals(id, broken.rowId());
        assertEquals(AuditLogService.Reason.CHAIN, broken.reason(),
                "a mis-linked row must be reported as a chain break");
    }

    private long writeOne(String targetId) throws InterruptedException {
        long baseline = maxId();
        auditLogWriter.writeAsync(1L, "admin", null, "integration", "ONE_" + targetId,
                targetId, "detail", "127.0.0.1", Instant.now());
        List<Map<String, Object>> rows = pollRows(baseline, 1);
        assertEquals(1, rows.size(), "the row must be written");
        return ((Number) rows.get(0).get("id")).longValue();
    }

    private long maxId() {
        Long max = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM audit_log", Long.class);
        return max == null ? 0L : max;
    }

    private String headHash(long id) {
        if (id == 0) {
            return null;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT row_hash FROM audit_log WHERE id = ?", id);
        return rows.isEmpty() ? null : (String) rows.get(0).get("row_hash");
    }

    private List<Map<String, Object>> pollRows(long baseline, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        List<Map<String, Object>> rows = List.of();
        while (System.currentTimeMillis() < deadline) {
            rows = jdbcTemplate.queryForList(
                    "SELECT id, prev_hash, row_hash FROM audit_log WHERE id > ? ORDER BY id", baseline);
            if (rows.size() >= expected) {
                return rows;
            }
            Thread.sleep(50);
        }
        return rows;
    }
}
