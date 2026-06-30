package com.example.medical.common.audit;

import com.example.medical.common.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

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
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Async audit write failed — action={} module={} targetId={}. "
                    + "Business transaction was already committed and is unaffected.",
                    action, module, targetId, e);
        }
    }
}
