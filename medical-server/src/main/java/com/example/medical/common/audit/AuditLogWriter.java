package com.example.medical.common.audit;

import com.example.medical.common.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Writes audit log entries asynchronously on the {@code auditExecutor}
 * thread pool.
 * <p>
 * Each write runs in its own {@code REQUIRES_NEW} transaction so a failure
 * here (e.g. DB connection blip) will <b>never</b> rollback the business
 * transaction that triggered the audit event.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogWriter {

    private final AuditLogRepository auditLogRepository;

    private final ReentrantLock chainLock = new ReentrantLock();

    @Async("auditExecutor")
    public void writeAsync(Long userId, String username, Long patientId,
                           String module, String action, String targetId,
                           String detail, String ip, Instant eventTime) {
        try {
            AuditLog entry = new AuditLog();
            entry.setUserId(userId);
            entry.setUsername(username);
            entry.setPatientId(patientId);
            entry.setModule(module);
            entry.setAction(action);
            entry.setTargetId(targetId);
            entry.setDetail(detail);
            entry.setIp(ip);
            entry.setCreateTime(LocalDateTime.ofInstant(eventTime, ZoneOffset.UTC));
            appendToChain(entry);
        } catch (Exception e) {
            log.error("Async audit write failed — action={} module={} targetId={}. "
                    + "Business transaction was already committed and is unaffected.",
                    action, module, targetId, e);
        }
    }

    /**
     * Hash chaining (Review III M2): link to the previous row so any tampering
     * breaks the chain.
     * <p>
     * Reading the head and inserting must not interleave: two audit threads that
     * both read the same head chain their rows from it and the chain forks — the
     * dev log forked exactly this way, rows 3 and 4 both linking to row 2
     * (R53-08). {@code save} is transactional, so the row is committed before
     * the lock is released and the next writer reads it. This serialises one
     * JVM; a multi-instance deployment would need a database-level lock on the
     * head row.
     */
    private void appendToChain(AuditLog entry) {
        chainLock.lock();
        try {
            auditLogRepository.findTopByOrderByIdDesc()
                    .ifPresent(last -> entry.setPrevHash(last.getRowHash()));
            auditLogRepository.save(entry);
        } finally {
            chainLock.unlock();
        }
    }
}
